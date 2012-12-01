package com.mprew.ec2.resources;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.mprew.ec2.resources.action.ResourceAction;
import com.mprew.ec2.resources.action.ResourceActions;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.context.MutableApplicationContext;
import com.mprew.ec2.resources.context.ResourceContext;
import com.mprew.ec2.resources.event.PhaseChangeEvent;
import com.mprew.ec2.resources.event.PhaseChangeListener;
import com.mprew.ec2.resources.event.ResourceEvent;
import com.mprew.ec2.resources.event.ResourceRegisteredEvent;
import com.mprew.ec2.resources.event.ResourceEvent.EventType;
import com.mprew.ec2.resources.event.ResourceFailedEvent;
import com.mprew.ec2.resources.event.ResourceListener;
import com.mprew.ec2.resources.event.ResourceUnregisteredEvent;
import com.mprew.ec2.resources.startup.DependencyException;
import com.mprew.ec2.resources.validation.ValidationException;

/**
 * The implementation of the ResourceManager with also implements by default the ResourceContext and of course, ResourceManagerOperations. Those two interfaces
 * exist merely to hide the additional functionality of the ResourceManager from public classpath space.
 * 
 * @author dgarson
 */
public class ResourceManager implements ResourceContext, InitializingBean, DisposableBean, ApplicationContextAware, ResourceManagerOperations {
	
	public static final ResourceHealth.Level TEST_UNSTABLE_LEVEL = ResourceHealth.Level.WARNING;
	
	private static final Logger log = LoggerFactory.getLogger(ResourceManager.class);
	static final ThreadLocal<ResourceManager> tlManager = new ThreadLocal<ResourceManager>();
	
	private ResourceState systemState;
	private volatile ResourceHealth systemHealth;
	// private boolean shutdownAllOnError = false;
	
	private final ExecutorService executor = Executors.newFixedThreadPool(6, new ResourceJobThreadFactory());
	private final ExecutorService eventDispatcher = Executors.newFixedThreadPool(6, new EventDispatchThreadFactory());
	private final ExecutorService phaseExecutor = Executors.newFixedThreadPool(6, new PhaseJobThreadFactory());
	private final Object resourceLock = new Object();
	private Map<String, ResourceMetadata> resourceMap = new ConcurrentHashMap<String, ResourceMetadata>();
	private Map<String, ResourceMetadata> beanMap = new ConcurrentHashMap<String, ResourceMetadata>();
	private Map<ResourceMetadata, Set<Thread>> workingOnResources = new ConcurrentHashMap<ResourceMetadata, Set<Thread>>();
	private Map<String, Object> properties = new ConcurrentHashMap<String, Object>();
	
	private final Map<ResourceFilter, List<ResourceListener>> listenerMap = new HashMap<ResourceFilter, List<ResourceListener>>();
	private final List<PhaseChangeListener> phaseListeners = new ArrayList<PhaseChangeListener>();
	private final Map<Class<?>, Injections> pendingInjections = new ConcurrentHashMap<Class<?>, Injections>();
	
	private DependencyCalculator calculator;
	
	private ApplicationContext appContext;
	private boolean startingUp = false;
	
	/**
	 * Default constructor, used by Spring to construct a ResourceManager instance that will become
	 * wired and initialized through <tt>InitializableBean</tt> and <tt>ApplicationContextAware</tt>.
	 */
	public ResourceManager() {
		// Default constructor for Spring startup
	}
	
	/**
	 * Creates a new ResourceManager around an existing ApplicationContext, during runtime.
	 * @param appContext the application context instance
	 * @throws Exception on any startup exceptions
	 */
	public ResourceManager(MutableApplicationContext appContext) throws Exception {
		this.appContext = appContext;
		afterPropertiesSet();
	}
	
	/**
	 * Checks if the resource manager is moving from INITIALIZED to STARTED.
	 * @return if the resource manager is starting up
	 */
	public boolean isStartingUp() {
		return startingUp;
	}
	
	/**
	 * Spawns a thread to perform our initialization.
	 */
	public void doStartup() {
		Thread startupThread = new Thread(new Runnable(){
			@Override
			public void run() {
				try {
					initialize();
				} catch (ResourceException re) {
					log.error("Unable to initialize resources", re);
				}
			}
		}, "InitThread");
		startupThread.start();
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
		// Set external references
		tlManager.set(this);
		
		// Set the resource manager for action and object construction
		ResourceActions.setResourceManager(this);
		ObjectFactory.resourceManager = this;
		
		systemState = ResourceState.INITIALIZING;
		systemHealth = ResourceHealth.okHealth;
		
		// Automatically discover managed beans
		Map<String, Object> managedBeans = appContext.getBeansWithAnnotation(ResourceType.class);
		for (Map.Entry<String, Object> entry : managedBeans.entrySet()) {
			// Create the resource entries
			ResourceType annotation = entry.getValue().getClass().getAnnotation(ResourceType.class);
			registerResource(annotation.name(), entry.getKey(), entry.getValue(), false, false);
		}
		
		synchronized (resourceMap) {
			// Check for ResourceContextAware interface and set Context if implemented
			for (ResourceMetadata resource : resourceMap.values()) {
				resource.setContextIfAware(this);
			}
		}
		
		calculator = new DependencyCalculator();
		for (ResourceMetadata resource : resourceMap.values()) {
			try {
				resource.validate();
			} catch (ValidationException ve) {
				throw new DependencyDeclarationException("Unable to validate " + resource, ve);
			}
			
			try {
				calculator.addDependency(resource);
			} catch (DependencyException de) {
				String msg = "Unable to add resource [" + resource.getResourceName() + "] due to unsatisfied dependencies!";
				log.error(msg, de);
				throw new DependencyDeclarationException(msg, de);
			}
		}
	}
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.appContext = applicationContext;
	}
	
	/**
	 * Adds a PhaseListener to listen to system state changes.
	 * @param listener the listener
	 */
	public void addPhaseListener(PhaseChangeListener listener) {
		synchronized (phaseListeners) {
			if (!phaseListeners.contains(listener)) {
				phaseListeners.add(listener);
			}
		}
	}
	
	/**
	 * Removes a PhaseListener from listening to system state changes.
	 * @param listener the listener
	 */
	public void removePhaseListener(PhaseChangeListener listener) {
		synchronized (phaseListeners) {
			phaseListeners.remove(listener);
		}
	}
	
	/**
	 * Adds a new ResourceListener that applies to all ResourceEvents.
	 * @param listener the listener
	 * @see #addResourceListener(ResourceFilter, ResourceListener)
	 * @see ResourceFilter#ACCEPT_EVERYTHING
	 */
	public void addResourceListener(ResourceListener listener) {
		if (listener instanceof ResourceFilter) {
			addResourceListener((ResourceFilter)listener, listener);
		}
		else {
			addResourceListener(ResourceFilter.ACCEPT_EVERYTHING, listener);
		}
	}
	
	/**
	 * Adds a new ResourceListener mapped to the given ResourceFilter (which may be null to indicate no filter).
	 * @param filter the resource filter
	 * @param listener the listener
	 */
	public void addResourceListener(ResourceFilter filter, ResourceListener listener) {
		synchronized (listenerMap) {
			if (filter == null) {
				filter = ResourceFilter.ACCEPT_EVERYTHING;
			}
			List<ResourceListener> listeners = listenerMap.get(filter);
			if (listeners == null) {
				listeners = new ArrayList<ResourceListener>();
				listenerMap.put(filter, listeners);
			}
			listeners.add(listener);
		}
	}
	
	/**
	 * Removes a ResourceListener from all filters that it is registered with.
	 * @param listener the listener instance
	 */
	public void removeResourceListener(ResourceListener listener) {
		if (listener instanceof ResourceFilter) {
			removeResourceListener((ResourceFilter)listener, listener);
		}
		else {
			removeResourceListener(null, listener);
		}
	}
	
	/**
	 * Removes a ResourceListener from the specified ResourceFilter mapping.
	 * @param filter the resource filter (may be null)
	 * @param listener the listener instance
	 */
	public void removeResourceListener(ResourceFilter filter, ResourceListener listener) {
		synchronized (listenerMap) {
			if (filter != null) {
				List<ResourceListener> listeners = listenerMap.get(filter);
				if (listeners != null) {
					listeners.remove(listener);
				}
				if (listeners != null && listeners.isEmpty()) {
					listenerMap.remove(filter);
				}
			}
			else {
				for (Iterator<ResourceFilter> keyIter = listenerMap.keySet().iterator(); keyIter.hasNext();) {
					filter = keyIter.next();
					List<ResourceListener> listeners = listenerMap.get(filter);
					if (listeners.remove(listener)) {
						if (listeners.isEmpty()) {
							keyIter.remove();
						}
					}
				}
			}
		}
	}
	
	/**
	 * Runs through the initialization of all resources.
	 * @throws ResourceException if there are exceptions during initialization
	 */
	public synchronized void initialize() throws ResourceException {
		Future<?> future;
		synchronized (resourceMap) {
			future = phaseExecutor.submit(ResourceActions.initialize(resourceMap.values(), true));
		}
		try {
			future.get();
			if (!pendingInjections.isEmpty()) {
				for (Map.Entry<Class<?>, Injections> entry : pendingInjections.entrySet()) {
					Class<?> resourceType = entry.getKey();
					ResourceInfo resource = getResource(resourceType); 
					if (resource == null) {
						StringBuilder resNames = new StringBuilder();
						for (Field field : entry.getValue().getFields()) {
							ResourceMetadata res = entry.getValue().getInjectee(field);
							if (resNames.length() > 0) {
								resNames.append(", ");
							}
							resNames.append(res.getResourceName());
						}
						throw new ResourceNotFoundException("Unable to find Dependency Resource [" + resourceType.getAnnotation(ResourceType.class).name() + "] of Type [" + resourceType + "] for Resource References [" + resNames + "]");
					}
				}
			}
			log.info("Finished running Initialization action.");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke initialization action", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for initialization to complete", ie);
		}
	}
	
	/**
	 * Starts all of the registered resources.
	 * @throws ResourceException if there are exceptions during startup
	 */
	public synchronized void start() throws ResourceException {
		startingUp = true;
		Future<?> future;
		synchronized (resourceMap) {
			future = phaseExecutor.submit(ResourceActions.start(resourceMap.values(), true));
		}
		try {
			future.get();
			startingUp = false;
			log.info("Finished running Start action.");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke startup action", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for initialization to complete", ie);
		}
	}
	
	@Override
	public Callable<?> startAsync(String resourceName) throws ResourceNotFoundException {
		synchronized (resourceMap) {
			ResourceMetadata resource = resourceMap.get(resourceName);
			if (resource == null) {
				throw new ResourceNotFoundException("Unrecognized resource: " + resourceName);
			}
			return ResourceActions.start(Arrays.asList(resource), false);
		}
	}
	
	@Override
	public void start(String resourceName) throws ResourceNotFoundException, ResourceException {
		Future<?> future = phaseExecutor.submit(startAsync(resourceName));
		try {
			future.get();
			log.info("Finished running Start action for Resource [" + resourceName + "]");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke start", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for Resume to complete", ie);
		}
	}
	
	/**
	 * Publishes all registered resources that are publishable. Any unpublishable resources shall be moved
	 * into the RUNNING state if possible.
	 * @throws ResourceException if any exceptions occur while publishing
	 */
	public synchronized void publish() throws ResourceException {
		Future<?> future;
		synchronized (resourceMap) {
			future = phaseExecutor.submit(ResourceActions.publish(resourceMap.values(), true));
		}
		try {
			future.get();
			log.info("Finished running Publish action.");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke publish action", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for initialization to complete", ie);
		}
	}
	
	@Override
	public Callable<?> publishAsync(String resourceName) throws ResourceNotFoundException {
		synchronized (resourceMap) {
			ResourceMetadata resource = resourceMap.get(resourceName);
			if (resource == null) {
				throw new ResourceNotFoundException("Unrecognized resource: " + resourceName);
			}
			return ResourceActions.publish(Arrays.asList(resource), false);
		}
	}
	
	@Override
	public void publish(String resourceName) throws ResourceNotFoundException, ResourceException, ImpossibleActionException {
		Future<?> future = phaseExecutor.submit(publishAsync(resourceName));
		try {
			future.get();
			log.info("Finished running Publish action for Resource [" + resourceName + "]");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke resume", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for Resume to complete", ie);
		}
	}
	
	/**
	 * Attempts to move all running resources into the PAUSED state, if possible. If there are any running resources that
	 * have references that cannot be paused, then an exception will be thrown.
	 * @throws ResourceException any exceptions while pausing
	 */
	public synchronized void pause() throws ResourceException {
		Future<?> future;
		synchronized (resourceMap) {
			future = phaseExecutor.submit(ResourceActions.pause(resourceMap.values(), true));
		}
		try {
			future.get();
			log.info("Finished running Pause action.");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke pause", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for Pause to complete", ie);
		}
	}
	
	@Override
	public Callable<?> pauseAsync(String resourceName) throws ResourceNotFoundException, ImpossibleActionException {
		synchronized (resourceMap) {
			ResourceMetadata resource = resourceMap.get(resourceName);
			if (resource == null) {
				throw new ResourceNotFoundException("Unrecognized resource: " + resourceName);
			}
			if (!resource.hasPause()) {
				throw new ImpossibleActionException("Cannot pause Resource [" + resourceName + "] because it has no @Pause method defined");
			}
			return ResourceActions.pause(Arrays.asList(resource), false);
		}
	}
	
	@Override
	public void pause(String resourceName) throws ResourceNotFoundException, ImpossibleActionException, ResourceException {
		Future<?> future = phaseExecutor.submit(pauseAsync(resourceName));
		try {
			future.get();
			log.info("Finished running Pause action for Resource [" + resourceName + "]");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke pause", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for Resume to complete", ie);
		}
	}
	
	/**
	 * Attempts to resume all paused resources, if possible.
	 * @throws ResourceException on any exceptions while resuming
	 */
	public synchronized void resume() throws ResourceException {
		Future<?> future;
		synchronized (resourceMap) {
			future = phaseExecutor.submit(ResourceActions.resume(resourceMap.values(), true));
		}
		try {
			future.get();
			log.info("Finished running Resume action.");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke resume", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for Resume to complete", ie);
		}
	}
	
	@Override
	public Callable<?> resumeAsync(String resourceName) throws ResourceNotFoundException, ImpossibleActionException {
		synchronized (resourceMap) {
			ResourceMetadata resource = resourceMap.get(resourceName);
			if (resource == null) {
				throw new ResourceNotFoundException("Unrecognized resource: " + resourceName);
			}
			if (!resource.hasPause()) {
				throw new ImpossibleActionException("Cannot resume Resource [" + resourceName + "] because it has no @Resume method defined");
			}
			return ResourceActions.resume(Arrays.asList(resource), false);
		}
	}
	
	@Override
	public void resume(String resourceName) throws ResourceNotFoundException, ResourceException {
		Future<?> future = phaseExecutor.submit(resumeAsync(resourceName));
		try {
			future.get();
			log.info("Finished running Resume action for Resource [" + resourceName + "]");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to invoke resume", ee.getCause());
		} catch (InterruptedException ie) {
			throw new ResourceException("Interrupted waiting for Resume to complete", ie);
		}
	}
	
	/**
	 * Attempts to stop all registered resources either gracefully or forcefully.
	 * @param forceful if true, forceful shutdown, otherwise graceful shutdown
	 * @throws ResourceException on any shutdown exception
	 */
	public synchronized void stop(boolean forceful) throws ResourceException {
		Future<?> result;
		synchronized (resourceMap) {
			result = phaseExecutor.submit(ResourceActions.stop(resourceMap.values(), forceful, true));
		}
		try {
			result.get();
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to execute ResourceStoppingTask for " + resourceMap.size() + " resources", ee.getCause());
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new ResourceException("Interrupted while waiting for " + resourceMap.size() + " resources to stop", ie);
		}
	}
	
	/**
	 * Attempts to perform a graceful shutdown.
	 * @throws ResourceException on any shutdown exception
	 */
	public synchronized void stop() throws ResourceException {
		stop(false);
	}
	
	@Override
	public Callable<?> stopAsync(String resourceName, boolean forceful) throws ResourceNotFoundException {
		synchronized (resourceMap) {
			ResourceMetadata resource = resourceMap.get(resourceName);
			if (resource == null) {
				throw new ResourceNotFoundException("Unrecognized resource: " + resourceName);
			}
			return ResourceActions.stop(Arrays.asList(resource), forceful, false);
		}
	}
	
	@Override
	public void stop(String resourceName, boolean forceful) throws ResourceNotFoundException, ResourceException {
		Future<?> result = phaseExecutor.submit(stopAsync(resourceName, forceful));
		try {
			result.get();
			log.info("Finished running Stop action for Resource [" + resourceName + "]");
		} catch (ExecutionException ee) {
			if (ee.getCause() instanceof ResourceException)
				throw (ResourceException)ee.getCause();
			throw new ResourceException("Unable to execute ResourceStoppingTask for " + resourceMap.size() + " resources", ee.getCause());
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new ResourceException("Interrupted while waiting for " + resourceMap.size() + " resources to stop", ie);
		}
	}
	
	/**
	 * Returns a job that can be submitted to stop the specified resource, if it exists.
	 * @param resourceName the resource name
	 * @return the job to stop the resource
	 * @throws ResourceNotFoundException if the resource does not exist
	 * @throws ResourceException on any exceptions while creating the job
	 */
	public Callable<?> stopAsync(String resourceName) throws ResourceNotFoundException, ResourceException {
		return stopAsync(resourceName, false);
	}
	
	/**
	 * Stops the resource with the given name.
	 * @param resourceName the resource name
	 * @throws ResourceNotFoundException if the resource could not be found
	 * @throws ResourceException on any exceptions while stopping
	 */
	public void stop(String resourceName) throws ResourceNotFoundException, ResourceException {
		stop(resourceName, false);
	}
	
	@Override
	public void destroy() throws Exception {
		try {
			stop(true);
			
			Thread.sleep(250);
			
			executor.shutdown();
			try {
				if (executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
					log.info("Successfully waited for executor to shutdown.");
				}
				else {
					log.warn("Failed to wait for executor to shutdown gracefully.");
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new ResourceException("Unable to wait for executor to shutdown");
			}
			
			phaseExecutor.shutdown();
			eventDispatcher.shutdown();
			
			logWorkingOn(log);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	/**
	 * Gets the job ExecutorService.
	 * @return the job executor
	 */
	ExecutorService getJobExecutor() {
		return executor;
	}
	
	/**
	 * Checks if this ResourceManager is working on the specified resource.
	 * @param resource the resource
	 * @return true if working on resource, false otherwise
	 */
	public boolean isWorkingOn(ResourceInfo resource) {
		synchronized (workingOnResources) {
			Set<Thread> threads = workingOnResources.get(resource);
			if (threads == null) {
				return false;
			}
			Set<Thread> otherThreads = new HashSet<Thread>(threads);
			otherThreads.remove(Thread.currentThread());
			return !otherThreads.isEmpty();
		}
	}
	
	/**
	 * Marks a resource as being acted upon.
	 * @param resource the resource to mark as being worked on
	 */
	void workingOn(ResourceInfo resource) {
		synchronized (workingOnResources) {
			Set<Thread> threads = workingOnResources.get(resource);
			if (threads == null) {
				threads = new HashSet<Thread>();
				workingOnResources.put((ResourceMetadata)resource, threads);
			}
			if (threads.add(Thread.currentThread())) {
				// log.info("Working On Resource [" + resource.getResourceName() + "]: " + toString(threads));
			}
		}
	}
	
	/**
	 * Marks a resource as no longer being acted upon.
	 * @param resource the resource to unmark as being worked on
	 */
	void finishedWith(ResourceInfo resource) {
		synchronized (workingOnResources) {
			Set<Thread> threads = workingOnResources.get(resource);
			if (threads != null) {
				if (threads.remove(Thread.currentThread())) {
					// log.info("Working On Resource[" + resource.getResourceName() + "]: " + toString(threads));
				}
				if (threads.isEmpty()) {
					workingOnResources.remove(resource);
				}
			}
		}
	}
	
	public void logWorkingOn(Logger log) {
		synchronized (workingOnResources) {
			StringBuilder buf = new StringBuilder("Working on Resources:");
			if (workingOnResources.isEmpty()) {
				buf.append(" None");
			}
			else {
				for (Map.Entry<ResourceMetadata, Set<Thread>> entry : workingOnResources.entrySet()) {
					ResourceMetadata resource = entry.getKey();
					Set<Thread> threads = entry.getValue();
					buf.append("\n\t").append(resource.getResourceName() + " [" + resource.getState() + "] Bean [" + resource.getBeanName() + "]:");
					for (Thread t : threads) {
						buf.append("\n\t\tThread: ").append(t.getName() + " [" + t.getId() + "]");
					}
				}
			}
			log.info(buf.toString());
		}
	}
	
	/**
	 * Check to see if we need to change to a new system state.
	 */
	void checkForNewSystemState() {
		boolean updateState = true;
		ResourceState newSystemState;
		synchronized (resourceMap) {
			// We are trying to move to the stable form of our state
			newSystemState = systemState.getStabilizeState();
			for (ResourceMetadata resource : resourceMap.values()) {
				// If there is any different then we won't update the system state
				if (!resource.getState().isEquivalent(newSystemState)) {
					updateState = false;
					break;
				}
			}
		}
		if (updateState) {
			changeSystemState(newSystemState);
			
			// If we are updating the system state then see if we need to correct the system health
			if (systemHealth.getLevel() == TEST_UNSTABLE_LEVEL) {
				updateSystemHealth();
			}
		}
	}
	
	/**
	 * Changes the system state.
	 * @param newState the new system state
	 */
	void changeSystemState(final ResourceState newState) {
		synchronized (resourceLock) {
			if (systemState == newState) {
				return;
			}
			String msg = "System state changed from " + systemState + " to " + newState;
			final ResourceState oldState = systemState;
			systemState = newState;
			log.info(msg);
			
			synchronized (phaseListeners) {
				final PhaseChangeEvent event = new PhaseChangeEvent(this, oldState, newState);
				for (final PhaseChangeListener listener : phaseListeners) {
					if (ResourceUtils.isSynchronous(listener)) {
						listener.phaseChanged(event);
					}
					else {
						// This shouldn't take a long time, but just in case
						eventDispatcher.submit(new Runnable(){
							@Override
							public void run() {
								listener.phaseChanged(event);
							}
						});
					}
				}
			}
			
			resourceLock.notifyAll();
		}
	}
	
	/**
	 * Fires a resource event for the specified resource.
	 * @param resource the resource
	 * @param eventType the event type
	 */
	void fireResourceEvent(ResourceMetadata resource, ResourceState oldState, EventType eventType) {
		// Construct a new ResourceEvent from the source and type
		final ResourceEvent evt;
		if (eventType == EventType.REGISTERED)
			evt = new ResourceRegisteredEvent(resource);
		else if (eventType == EventType.UNREGISTERED)
			evt = new ResourceUnregisteredEvent(resource);
		else
			evt = new ResourceEvent(resource, eventType);
		synchronized (listenerMap) {
			// Keep track of the invoked ResourceListeners in case there are overlapping listeners registered w/ResourceFilters that match the same resource
			Set<ResourceListener> invoked = new HashSet<ResourceListener>();
			for (Map.Entry<ResourceFilter, List<ResourceListener>> entry : listenerMap.entrySet()) {
				if (entry.getKey().accepts(resource)) {
					for (final ResourceListener listener : entry.getValue()) {
						if (invoked.add(listener)) {
							if (ResourceUtils.isSynchronous(listener)) {
								// Pass off the event to the listener
								listener.onResourceEvent(evt);
							}
							else {
								eventDispatcher.submit(new Runnable(){
									@Override
									public void run() {
										// Pass off the event to the listener
										listener.onResourceEvent(evt);
									}
								});
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Fires a resource failure for the specified resource with a Throwable cause.
	 * @param resource the resource
	 * @param attemptedEventType the attempted event type
	 * @param cause the Throwable cause
	 */
	void fireResourceFailure(ResourceMetadata resource, EventType attemptedEventType, Throwable cause) {
		final ResourceFailedEvent evt = new ResourceFailedEvent(resource, attemptedEventType, cause);
		synchronized (listenerMap) {
			// Keep track of the invoked ResourceListeners in case there are overlapping listeners registered w/ResourceFilters that match the same resource
			Set<ResourceListener> invoked = new HashSet<ResourceListener>();
			for (Map.Entry<ResourceFilter, List<ResourceListener>> entry : listenerMap.entrySet()) {
				if (entry.getKey().accepts(resource)) {
					for (final ResourceListener listener : entry.getValue()) {
						if (invoked.add(listener)) {
							if (ResourceUtils.isSynchronous(listener)) {
								// Pass off the event to the listener
								listener.onResourceFailure(evt);
							}
							else {
								eventDispatcher.submit(new Runnable(){
									@Override
									public void run() {
										// Pass off the event to the listener
										listener.onResourceFailure(evt);									
									}
								});
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Changes the state of one of the running resources.
	 * @param resource the resource
	 * @param currentState the current state
	 * @param newState the new state of the resource
	 * @return any ResourceException that will be thrown after everything else has changed
	 */
	ResourceException changeResourceState(ResourceInfo ri, ResourceState currentState, ResourceState newState, boolean updateSystemHealth) {
		if(currentState == newState) {
			return null;
		}
		ResourceMetadata resource = (ResourceMetadata)ri;
		EventType eventType = EventType.UNKNOWN;
		try {
			resource.semaphore.acquire();
			switch (currentState) {
				case INITIALIZING:
					switch (newState) {
						case INITIALIZED:
							eventType = EventType.INITIALIZING;
							resource.setContextIfAware(this);
							try {
								resource.injectProperties(this, pendingInjections);
							} catch (ResourceException re) {
								log.warn("Unable to inject properties into " + resource, re);
							}
							if (resource.hasInitialize()) {
								resource.initialize(this);
							}
							eventType = EventType.INITIALIZED;
							setResourceState(resource, currentState, ResourceState.INITIALIZED, updateSystemHealth);
							break;
					}
					break;
					
				case RESUMING:
				case STARTING:
				case PUBLISHING:
				case PAUSING:
				case SHUTTING_DOWN_FORCEFULLY:
				case SHUTTING_DOWN_GRACEFULLY:
				case SHUTDOWN_FORCEFULLY:
				case SHUTDOWN_GRACEFULLY:
					// no-op
					break;
				
				// The resource is currently in the INITIALIZED state
				case INITIALIZED:
					switch (newState) {
						case INITIALIZING:
						case INITIALIZED:
							// no-op
							break;
						case STARTING:
						case STARTED:
							eventType = EventType.STARTING;
							setResourceState(resource, currentState, ResourceState.STARTING, updateSystemHealth);
							try {
								resource.injectProperties(this, pendingInjections);
							} catch (ResourceException re) {
								log.warn("Unable to inject properties into " + resource, re);
							}
							resource.start(this);
							eventType = EventType.STARTED;
							setResourceState(resource, currentState, ResourceState.STARTED, updateSystemHealth);
							break;
						case PUBLISHING:
						case RUNNING:
							// If we are resuming then we should start an initialized resource
						case RESUMING:
							eventType = EventType.STARTING;
							setResourceState(resource, currentState, ResourceState.STARTING, updateSystemHealth);
							try {
								resource.injectProperties(this, pendingInjections);
							} catch (ResourceException re) {
								log.warn("Unable to inject properties into " + resource, re);
							}
							resource.start(this);
							eventType = EventType.STARTED;
							setResourceState(resource, currentState, ResourceState.STARTED, updateSystemHealth);
							if (resource.hasPublish()) {
								eventType = EventType.PUBLISHING;
								setResourceState(resource, currentState, ResourceState.PUBLISHING, updateSystemHealth);
								try {
									resource.injectProperties(this, pendingInjections);
								} catch (ResourceException re) {
									log.warn("Unable to inject properties into " + resource, re);
								}
								resource.publish(this);
							}
							eventType = EventType.RUNNING;
							setResourceState(resource, currentState, ResourceState.RUNNING, updateSystemHealth);
							break;
						case PAUSING:
						case PAUSED:
							// do nothing; we should hold at INITIALIZED
							break;
						case SHUTTING_DOWN_FORCEFULLY:
						case SHUTDOWN_FORCEFULLY:
							eventType = EventType.STOPPING;
							forcefulShutdown(resource, currentState, updateSystemHealth);
							break;
						case SHUTTING_DOWN_GRACEFULLY:
						case SHUTDOWN_GRACEFULLY:
							eventType = EventType.STOPPING;
							gracefulShutdown(resource, currentState, updateSystemHealth);
							break;
					}
					break;
					
				// The resource is currently in the STARTED state
				case STARTED:
					switch (newState) {
						case INITIALIZING:
						case INITIALIZED:
						case STARTING:
						case STARTED:
							// no-op
							break;
						case PUBLISHING:
						case RUNNING:
						case RESUMING:
							if (resource.hasPublish()) {
								eventType = EventType.PUBLISHING;
								setResourceState(resource, currentState, ResourceState.PUBLISHING, updateSystemHealth);
								resource.publish(this);
							}
							eventType = EventType.RUNNING;
							setResourceState(resource, currentState, ResourceState.RUNNING, updateSystemHealth);
							break;
						case PAUSING:
						case PAUSED:
							// nothing to do, we should hold at STARTED
							break;
						case SHUTTING_DOWN_FORCEFULLY:
						case SHUTDOWN_FORCEFULLY:
							eventType = EventType.STOPPING;
							forcefulShutdown(resource, currentState, updateSystemHealth);
							break;
						case SHUTTING_DOWN_GRACEFULLY:
						case SHUTDOWN_GRACEFULLY:
							eventType = EventType.STOPPING;
							gracefulShutdown(resource, currentState, updateSystemHealth);
							break;
					}
					break;
				
				// The resource is currently in the RUNNING state
				case RUNNING:
					switch (newState) {
						case INITIALIZING:
						case INITIALIZED:
						case STARTING:
						case STARTED:
						case PUBLISHING:
						case RUNNING:
						case RESUMING:
							// no-op
							break;
						case PAUSING:
						case PAUSED:
							if (resource.getResourceMethod(ResourceAction.PAUSING) != null) {
								eventType = EventType.PAUSING;
								setResourceState(resource, currentState, ResourceState.PAUSING, updateSystemHealth);
								resource.pause(this);
								eventType = EventType.PAUSED;
								setResourceState(resource, currentState, ResourceState.PAUSED, updateSystemHealth);
							}
							break;
						case SHUTTING_DOWN_FORCEFULLY:
						case SHUTDOWN_FORCEFULLY:
							eventType = EventType.STOPPING;
							forcefulShutdown(resource, currentState, updateSystemHealth);
							break;
						case SHUTTING_DOWN_GRACEFULLY:
						case SHUTDOWN_GRACEFULLY:
							eventType = EventType.STOPPING;
							gracefulShutdown(resource, currentState, updateSystemHealth);
							break;
					}
					break;
				
				// The resource is currently in the PAUSED state
				case PAUSED:
					switch (newState) {
						case INITIALIZING:
						case INITIALIZED:
						case STARTING:
						case STARTED:
						case PAUSING:
						case PAUSED:
						case PUBLISHING:
							// no-op
							break;
						case RESUMING:
						case RUNNING:
							if (resource.getResourceMethod(ResourceAction.PAUSING) != null) {
								eventType = EventType.RESUMING;
								setResourceState(resource, currentState, ResourceState.RESUMING, updateSystemHealth);
								resource.resume(this);
								eventType = EventType.RESUMED;
								setResourceState(resource, currentState, ResourceState.RUNNING, updateSystemHealth);
							}
							break;
						case SHUTTING_DOWN_FORCEFULLY:
						case SHUTDOWN_FORCEFULLY:
							eventType = EventType.STOPPING;
							forcefulShutdown(resource, currentState, updateSystemHealth);
							break;
						case SHUTTING_DOWN_GRACEFULLY:
						case SHUTDOWN_GRACEFULLY:
							eventType = EventType.STOPPING;
							gracefulShutdown(resource, currentState, updateSystemHealth);
							break;
					}
					break;
			}
		} catch (Exception e) {
			
			// Fire the failure event first
			fireResourceFailure(resource, eventType, e);
			
			// If we throw an exception as we are shutting down, simply consider the resource shutdown
			switch (newState) {
				case SHUTDOWN_FORCEFULLY:
				case SHUTTING_DOWN_FORCEFULLY:
					log.error("Resource[" + resource.getResourceName() + "] moving to state " + newState + " threw exception", e);
					setResourceState(resource, currentState, ResourceState.SHUTDOWN_FORCEFULLY, false);
					break;
				case SHUTDOWN_GRACEFULLY:
				case SHUTTING_DOWN_GRACEFULLY:
					log.error("Resource[" + resource.getResourceName() + "] moving to state " + newState + " threw exception", e);
					setResourceState(resource, currentState, ResourceState.SHUTDOWN_GRACEFULLY, false);
					break;
				default:
					// Everything else we should shutdown forcefully and exit
					try {
						// Tell someone about it here since we've seen the following shutdown hang
						log.error(resource + " moving to state " + newState + " threw exception", e);
						if (((ResourceMetadata)resource).hasKill()) {
							resource.kill(this);
						}
						else {
							resource.stop(this);
						}
					} catch (Exception e2) {
						// Fire a second failure event in case we want to log it?
						fireResourceFailure(resource, EventType.STOPPING, e2);
						
						// We are already throwing an exception here so we ignore this if the resource is really crapping out
					}
					setResourceState(resource, currentState, ResourceState.SHUTDOWN_FORCEFULLY, false);
					break;
			}
			unregisterResource(resource);
			if (e instanceof ResourceException) {
				return (ResourceException)e;
			}
			else {
				return new ResourceException("Failed to change state of Resource[" + resource.getResourceName() + "] to " + newState, e);
			}
		} finally {
			resource.semaphore.release();
		}
		return null;
	}
	
	private void forcefulShutdown(ResourceMetadata resource, ResourceState currentState, boolean updateSystemState) throws ResourceException {
		if (resource.hasKill()) {
			setResourceState(resource, currentState, ResourceState.SHUTTING_DOWN_FORCEFULLY, updateSystemState);
			resource.kill(this);
			setResourceState(resource, currentState, ResourceState.SHUTDOWN_FORCEFULLY, updateSystemState);
		}
		else {
			setResourceState(resource, currentState, ResourceState.SHUTTING_DOWN_GRACEFULLY, updateSystemState);
			resource.stop(this);
			setResourceState(resource, currentState, ResourceState.SHUTDOWN_GRACEFULLY, updateSystemState);
		}
		unregisterResource(resource);
	}
	
	private void gracefulShutdown(ResourceMetadata resource, ResourceState currentState, boolean updateSystemState) throws ResourceException {
		setResourceState(resource, currentState, ResourceState.SHUTTING_DOWN_GRACEFULLY, updateSystemState);
		resource.stop(this);
		setResourceState(resource, currentState, ResourceState.SHUTDOWN_GRACEFULLY, updateSystemState);
		unregisterResource(resource);
	}
	
	public void updateResourceState(String resourceName, ResourceState newState) throws ResourceException {
		if (systemState == null) {
			throw new ResourceException("afterPropertiesSet() must be called on " + getClass().getName());
		}
		else if (newState == null) {
			throw new IllegalArgumentException("Unable to update a resource to a NULL state");
		}
		
		ResourceMetadata resource = (ResourceMetadata)getResource(resourceName);
		ResourceState currentState = resource.getState();
		if (!currentState.isNewStateOk(newState)) {
			throw new ResourceException("Cannot change resource state from " + currentState + " to " + newState + " for " + resource);
		}
		setResourceState(resource, currentState, newState, false);
	}
	
	void setResourceState(ResourceMetadata resource, ResourceState currentState, ResourceState newState, boolean updateSystemState) {
		log.debug(resource + " is being changed from " + currentState + " to " + newState);
		EventType eventType = EventType.fromState(currentState, newState);
		resource.setState(newState);
		if (eventType != EventType.UNKNOWN) {
			fireResourceEvent(resource, currentState, eventType);
		}
		if (newState.isStableState() && updateSystemState) {
			checkForNewSystemState();
		}
		if (newState == ResourceState.SHUTDOWN_FORCEFULLY || newState == ResourceState.SHUTDOWN_GRACEFULLY) {
			workingOnResources.remove(resource);
		}
	}
	
	private void updateSystemHealth() {
		ResourceHealth newHealth = ResourceHealth.okHealth;
		synchronized (resourceMap) {
			for (ResourceMetadata resource : resourceMap.values()) {
				ResourceHealth health = resource.getHealth();
				if (health.isWorse(newHealth)) {
					newHealth = health;
				}
			}
		}
		updateSystemHealth(newHealth);
	}
	
	private void updateSystemHealth(ResourceHealth newHealth) {
		String msg = null;
		if (newHealth.getLevel() == systemHealth.getLevel()) {
			switch (systemHealth.getLevel()) {
				case OK:
					// no message
					break;
				case WARNING:
				case ERROR:
					msg = "Health is still " + systemHealth;
					break;
			}
		}
		else {
			msg = "Health has changed to " + newHealth + " from " + systemHealth;
		}
		// Message might change
		systemHealth = newHealth;
		
		if (msg != null) {
			switch (systemHealth.getLevel()) {
				case OK:
					log.info("Health OK: " + msg);
					break;
				case WARNING:
					log.warn("Health WARNING: " + msg);
					break;
				case ERROR:
					log.error("Health ERROR: " + msg);
					break;
			}
		}
	}
	
	/**
	 * Registers a managed resource with its metadata.
	 * @param resName the resource name
	 * @param beanName the bean name
	 * @param instance the bean instance
	 * @param autowire if the bean should be auto-wired
	 * @param validateWithDeps if the bean should be validated with dependencies
	 */
	void registerResource(String resName, String beanName, Object instance, boolean autowire, boolean validateWithDeps) {
		ResourceMetadata metadata = new ResourceMetadata(resName, beanName, instance);
		registerResource(metadata, autowire, validateWithDeps);
	}
	
	/**
	 * Registers a managed resource with its metadata and explicitly specified start and stop methods that need not be annotated.
	 * @param resName the resource name
	 * @param beanName the bean name
	 * @param instance the object instance
	 * @param startMethod the start method
	 * @param stopMethod the stop method
	 * @param autowire if the bean should be auto-wired
	 * @param validateWithDeps if the bean should be validated with dependencies
	 */
	void registerResource(String resName, String beanName, Object instance, Method startMethod, Method stopMethod, boolean autowire, boolean validateWithDeps) {
		ResourceMetadata metadata = new ResourceMetadata(resName, beanName, instance, startMethod, stopMethod);
		if ((appContext instanceof MutableApplicationContext) && !appContext.containsBean(beanName)) {
			((MutableApplicationContext)appContext).registerBean(beanName, instance);
		}
		registerResource(metadata, autowire, validateWithDeps);
	}
	
	/**
	 * Registers a managed resource with its metadata, explicitly specifying the <i>names</i> of start and stop methods that are on the bean.
	 * @param resName the resource name
	 * @param beanName the bean name
	 * @param instance the object instance
	 * @param startMethodName the start method name
	 * @param stopMethodName the stop method name
	 * @param autowire if the bean should be auto-wired
	 * @param validateWithDeps if the bean should be validated with dependencies
	 * @throws IllegalArgumentException if any method specified is invalid
	 */
	void registerResource(String resName, String beanName, Object instance, String startMethodName, String stopMethodName, boolean autowire, boolean validateWithDeps) throws IllegalArgumentException {
		Method startMethod, stopMethod;
		try {
			startMethod = ResourceUtils.findMethod(instance.getClass(), startMethodName, new Class<?>[][] { { ResourceContext.class }, {} });
		} catch (NoSuchMethodException nsme) {
			throw new IllegalArgumentException(nsme);
		}
		try {
			stopMethod = ResourceUtils.findMethod(instance.getClass(), stopMethodName, new Class<?>[][] { { ResourceContext.class }, {} });
		} catch (NoSuchMethodException nsme) {
			throw new IllegalArgumentException(nsme);
		}
		registerResource(resName, beanName, instance, startMethod, stopMethod, autowire, validateWithDeps);
	}
	
	/**
	 * Registers an existing bean from the application context as a managed resource, specifying the <i>names</i> of start and stop methods on the bean.
	 * @param resName the resource name
	 * @param beanName the bean name
	 * @param startMethodName the start method name
	 * @param stopMethodName the stop method name
	 * @param autowire if the bean should be auto-wired
	 * @throws IllegalArgumentException if any method name is invalid or the bean name cannot be found
	 */
	@Override
	public void registerResource(String resName, String beanName, String startMethodName, String stopMethodName, boolean autowire) throws IllegalArgumentException
	{
		Object bean;
		try {
			bean = appContext.getBean(beanName);
		} catch (NoSuchBeanDefinitionException nsbde) {
			throw new IllegalArgumentException("Invalid bean name: " + beanName, nsbde);
		}
		registerResource(resName, beanName, bean, startMethodName, stopMethodName, autowire, true);
	}
	
	private void registerResource(ResourceMetadata metadata, boolean autowire, boolean validateWithDeps) {
		String resName = metadata.getResourceName();
		String beanName = metadata.getBeanName();
		if (resourceMap.put(resName, metadata) != null) {
			throw new IllegalStateException("Duplicate resource declaration [" + resName + "]");
		}
		else if (beanName != null) {
			if (beanMap.put(beanName, metadata) != null) {
				throw new IllegalStateException("Duplicate bean usage [" + beanName + "]");
			}
		}
		
		if (validateWithDeps) {
			try {
				metadata.validate();
			} catch (ValidationException ve) {
				throw new DependencyDeclarationException("Unable to validate " + metadata, ve);
			}
			
			try {
				calculator.addDependency(metadata);
			} catch (DependencyException de) {
				String msg = "Unable to add resource [" + resName + "] due to unsatisfied dependencies!";
				log.error(msg, de);
				throw new DependencyDeclarationException(msg, de);
			}		
		}
		
		// Fire registration event
		fireResourceEvent(metadata, ResourceState.INITIALIZING, EventType.REGISTERED);
		
		// Check if we should autowire properties already
		if (autowire) {
			try {
				metadata.injectProperties(this, pendingInjections);
			} catch (ResourceException re) {
				log.warn("Unable to inject properties into Resource [" + resName + "]", re);
			}
		}
		
		synchronized (pendingInjections) {
			for (Iterator<Map.Entry<Class<?>, Injections>> iter = pendingInjections.entrySet().iterator(); iter.hasNext();) {
				Map.Entry<Class<?>, Injections> entry = iter.next();
				Class<?> resourceType = entry.getKey();
				if (resourceType.isInstance(metadata.getInstance())) {
					log.info("Performing injections of " + resourceType + " in references!");
					Injections injs = entry.getValue();
					iter.remove();
					for (Field field : injs.getFields()) {
						ResourceMetadata refRes = injs.getInjectee(field);
						try {
							field.set(refRes.getInstance(), metadata.getInstance());
						} catch (IllegalAccessException iae) {
							log.error("Unable to inject Field [" + field.getName() + "] on Resource [" + refRes.getResourceName() + "] of Type [" + resourceType + "]", iae);
						} catch (IllegalArgumentException iae) {
							log.error("Unable to inject Field [" + field.getName() + "] on Resource [" + refRes.getResourceName() + "] of Type [" + resourceType + "]", iae);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Unregisters a resource by its resource name. 
	 * @param resourceName the resource name
	 * @throws ResourceNotFoundException if the resource could not be found by name
	 */
	void unregisterResource(String resourceName) throws ResourceNotFoundException {
		ResourceMetadata resource;
		synchronized (resourceMap) {
			resource = resourceMap.get(resourceName);
		}
		if (resource == null) {
			throw new ResourceNotFoundException("Unable to find resource: " + resourceName);
		}
		unregisterResource(resource);
	}
	
	/**
	 * Unregisters a singular managed resource by its metadata.
	 * @param resource the resource to unregister
	 */
	private void unregisterResource(ResourceMetadata resource) {
		if (!resource.getState().isEquivalent(ResourceState.SHUTDOWN_FORCEFULLY) &&
			!resource.getState().isEquivalent(ResourceState.SHUTDOWN_GRACEFULLY)) {
			try {
				log.info("Stopping Resource [" + resource.getResourceName() + "] prior to unregistration");
				stop(resource.getResourceName(), true);
				return;
			} catch (ResourceException re) {
				String msg = "Unable to stop resource prior to unregistration: " + resource.getResourceName();
				log.warn(msg, re);
				throw new IllegalStateException(msg, re);
			}
		}
		
		synchronized (resourceMap) {
			boolean removed = (resourceMap.remove(resource.getResourceName()) != null);
			if (!removed) {
				throw new IllegalStateException("Unable to unregister non-existent resource: " + resource.getResourceName());
			}
			if (resource.getBeanName() != null) {
				beanMap.remove(resource.getBeanName());
			}
			
			// Fire unregistration event
			fireResourceEvent(resource, resource.getState(), EventType.UNREGISTERED);
		}
		checkForNewSystemState();
	}
	
	/**
	 * Gets the metadata describing the given named resource.
	 * @param resourceName the resource name
	 * @return the resource metadata
	 * @throws ResourceException if the resource could not be found
	 */
	@Override
	public ResourceInfo getResource(String resourceName) throws ResourceNotFoundException {
		ResourceMetadata meta;
		synchronized (resourceMap) {
			meta = resourceMap.get(resourceName);
		}
		if (meta == null) {
			throw new ResourceNotFoundException("Unable to locate resource: " + resourceName);
		}
		return meta;
	}
	
	@Override
	public <T> Future<T> futureForResource(String resourceName, ResourceState state) {
		return executor.submit(new WaitForResourceTask<T>(resourceName, state));
	}
	
	@Override
	public <T> Future<T> futureForResource(String resourceName) {
		return executor.submit(new WaitForResourceTask<T>(resourceName, ResourceState.INITIALIZING));
	}
	
	/**
	 * Waits until the ResourceManager has been shut down. Used by any main() method to loop until done
	 * with program execution.
	 */
	@Override
	public void waitForShutdown() {
		waitForStates(ResourceState.SHUTDOWN_FORCEFULLY, ResourceState.SHUTDOWN_GRACEFULLY);
	}
	
	/**
	 * Waits for the ResourceManager to get to one of any number of states.
	 * @param states the valid states to trigger return
	 * @return the state of the system that was found and matched a provided state
	 */
	@Override
	public ResourceState waitForStates(ResourceState ... states) {
		synchronized (resourceLock) {
			while (true) {
				for (ResourceState state : states) {
					if (systemState == state) {
						return state;
					}
				}
				try {
					resourceLock.wait(30000);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
	
	/**
	 * Waits for a particular resource to enter into the specified state.
	 * @param resourceName the resource name
	 * @param resourceState the resource state to check for
	 * @throws InterruptedException if interrupted while waiting
	 * @throws ResourceNotFoundException if the resource does not exist
	 * @return true if the resource entered the given state, false otherwise
	 */
	@Override
	public boolean waitForResourceState(String resourceName, ResourceState resourceState) throws InterruptedException, ResourceNotFoundException {
		ResourceMetadata resource;
		synchronized (resourceMap) {
			resource = resourceMap.get(resourceName);
		}
		if (resource == null) {
			throw new ResourceNotFoundException("Unable to locate resource with name [" + resourceName + "]");
		}
		tlManager.set(this);
		return resource.waitForState(resourceState);
	}
	
	/**
	 * Gets the state of the system.
	 * @return the system state
	 */
	public ResourceState getSystemState() {
		synchronized (resourceLock) {
			return systemState;
		}
	}
	
	/**
	 * Gets the health of the system.
	 * @return the system health
	 */
	public ResourceHealth getSystemHealth() {
		return systemHealth;
	}
	
	@Override
	public ResourceInfo getResource(Class<?> resourceClass) throws ResourceNotFoundException, ResourceUniquenessException {
		ResourceMetadata match = null;
		synchronized (resourceMap) { 
			for (ResourceMetadata resource : resourceMap.values()) {
				if (resourceClass.isAssignableFrom(resource.getBeanClass())) {
					if (match != null) {
						throw new ResourceUniquenessException("Expected one resource of type [" + resourceClass + "] but found more than one!");
					}
					else {
						match = resource;
					}
				}
			}
		}
		if (match == null) {
			throw new ResourceNotFoundException("Unable to locate resource of type [" + resourceClass + "]");
		}
		return match;
	}
	
	@Override
	public ResourceManagerOperations getOperations() {
		return this;
	}
	
	@Override
	public Object getProperty(String key) {
		return properties.get(key);
	}
	
	@Override
	public boolean hasProperty(String key) {
		return properties.containsKey(key);
	}
	
	@Override
	public void setProperty(String key, Object value) {
		properties.put(key, value);
	}
	
	@Override
	public Object removeProperty(String key) {
		return properties.remove(key);
	}
	
	/**
	 * ThreadFactory for building ResourceJob threads.
	 * 
	 * @author dgarson
	 */
	private static class ResourceJobThreadFactory implements ThreadFactory {
		private AtomicInteger threadCounter = new AtomicInteger(0);
		
		@Override
		public Thread newThread(Runnable job) {
			Thread thread = new Thread(job, "ResourceJob-" + threadCounter.incrementAndGet());
			thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
				@Override
				public void uncaughtException(Thread th, Throwable err) {
					log.error("Unexpected exception in thread [" + th.getName() + "]", err);
				}
			});
			return thread;
		}
	}
	
	/**
	 * ThreadFactory for phase jobs.
	 * 
	 * @author dgarson
	 */
	private static class PhaseJobThreadFactory implements ThreadFactory {
		private AtomicInteger threadCounter = new AtomicInteger(0);
		
		@Override
		public Thread newThread(Runnable job) {
			Thread thread = new Thread(job, "PhaseJob-" + threadCounter.incrementAndGet());
			thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
				@Override
				public void uncaughtException(Thread th, Throwable err) {
					log.error("Unexpected exception in thread [" + th.getName() + "]", err);
				}
			});
			return thread;
		}
	}
	
	/**
	 * ThreadFactory for event dispatch threads.
	 * 
	 * @author dgarson
	 */
	private static class EventDispatchThreadFactory implements ThreadFactory {
		private AtomicInteger threadCounter = new AtomicInteger(0);
		
		@Override
		public Thread newThread(Runnable job) {
			Thread thread = new Thread(job, "EventDispatch-" + threadCounter.incrementAndGet());
			thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
				@Override
				public void uncaughtException(Thread th, Throwable err) {
					log.error("Unexpected exception in thread [" + th.getName() + "]", err);
				}
			});
			return thread;
		}
	}
	
	/**
	 * Class that encapsulates future injections that should be performed for a particular resource type.
	 * 
	 * @author dgarson
	 */
	static class Injections
	{
		private final Class<?> resourceType;
		private final Map<Field, ResourceMetadata> resourceFields;
		
		public Injections(Class<?> resourceType) {
			this.resourceFields = new HashMap<Field, ResourceMetadata>();
			this.resourceType = resourceType;
		}
		
		public Class<?> getResourceType() {
			return resourceType;
		}
		
		public void addInjection(ResourceMetadata referer, Field field) {
			resourceFields.put(field, referer);
		}
		
		public ResourceMetadata getInjectee(Field field) {
			return resourceFields.get(field);
		}
		
		public Set<Field> getFields() {
			return resourceFields.keySet();
		}
	}
	
	/**
	 * Encapsulates a task that is designed to wait for a resource with the given name to be registered, or for a phase change
	 * entering the shutdown states, in which failure is automatically detected.
	 * 
	 * @author dgarson
	 */
	private class WaitForResourceTask<T> implements Callable<T>, ResourceListener, PhaseChangeListener {
		private final String resourceName;
		private final ResourceState state;
		private final CountDownLatch latch = new CountDownLatch(1);
		private boolean failed = false;
		
		WaitForResourceTask(String resourceName, ResourceState state) {
			this.resourceName = resourceName;
			this.state = state;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public T call() throws ResourceNotFoundException, InterruptedException {
			boolean addedListener = false;
			synchronized (resourceMap) {
				// Check if we already have a resource with the given name
				if (resourceMap.containsKey(resourceName) && state == ResourceState.INITIALIZING) {
					log.info("Woke up due to resource already existing: " + resourceName);
					latch.countDown();
				}
				// Otherwise, add listeners to wake us up when we are ready for the resource
				else {
					addResourceListener(this);
					addPhaseListener(this);
					addedListener = true;
				}
			}
			// Await the countdown - which may proceed immediately depending on whether the resource already exists
			try {
				latch.await();
			} finally {
				// Only if we added the listeners, remove them now
				if (addedListener) {
					removeResourceListener(this);
					removePhaseListener(this);
				}
			}
			// If we failed to find the resource but ended up here, we should still throw an exception
			if (failed) {
				throw new ResourceNotFoundException("Failed to initialize or start resource: " + resourceName);
			}
			// Attempt to retrieve the resource from the manager and return its instance
			try {
				return (T)getResource(resourceName).getInstance();
			} catch (ClassCastException cce) {
				// Just in case the requested type is not the type of the resource, we should catch this exception
				throw new ResourceNotFoundException("Resource [" + resourceName + "] is not of the requested type");
			}
		}
		
		@Override
		public void onResourceEvent(ResourceEvent event) {
			// Check if we were waiting for a registration event
			if (state == ResourceState.INITIALIZING && event.getType() == ResourceEvent.EventType.REGISTERED) {
				// Next make sure that it applies to the same resource
				if (event.getResourceName().equalsIgnoreCase(resourceName)) {
					log.trace("Woke up due to " + resourceName + " being registered!");
					latch.countDown();
				}
			}
			// Check if the state is equivalent to the event that was received
			else if (event.getType().isEquivalent(state)) {
				// Next make sure that it applies to the same resource
				if (event.getResourceName().equalsIgnoreCase(resourceName)) {
					log.trace("Woke up due to " + resourceName + " entering " + state);
					latch.countDown();
				} 
			}
		}
		
		@Override
		public void onResourceFailure(ResourceFailedEvent event) {
			// If this is the resource we are waiting for, we have failed since the resource could not enter a new state
			if (event.getResourceName().equalsIgnoreCase(resourceName)) {
				log.trace("Woke up due to " + resourceName + " failing to perform an action");
				failed = true;
				latch.countDown();
			}
		}
		
		@Override
		public void phaseChanged(PhaseChangeEvent event) {
			// We know we have to be done with our countdown now, but the question is if we ended in error
			if (event.getNewState() == ResourceState.SHUTDOWN_FORCEFULLY ||
				event.getNewState() == ResourceState.SHUTDOWN_GRACEFULLY) {
				// If we are entering a shutdown state and we weren't waiting for the shutdown state, then we have failed!
				if (state != ResourceState.SHUTDOWN_FORCEFULLY &&
					state != ResourceState.SHUTDOWN_GRACEFULLY) {
					failed = true;
					latch.countDown();
				}
				// Otherwise, we were waiting for shutdown so we can leave failed as false
				else {
					latch.countDown();
				}
			}
		}
	}
}
