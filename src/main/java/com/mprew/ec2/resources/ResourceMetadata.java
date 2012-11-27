package com.mprew.ec2.resources;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import com.mprew.ec2.resources.ResourceManager.Injections;
import com.mprew.ec2.resources.action.ResourceAction;
import com.mprew.ec2.resources.annotation.ContextResource;
import com.mprew.ec2.resources.annotation.ContextProperty;
import com.mprew.ec2.resources.annotation.Dependencies;
import com.mprew.ec2.resources.annotation.Dependency;
import com.mprew.ec2.resources.annotation.Initialize;
import com.mprew.ec2.resources.annotation.Kill;
import com.mprew.ec2.resources.annotation.Pause;
import com.mprew.ec2.resources.annotation.Publish;
import com.mprew.ec2.resources.annotation.Resume;
import com.mprew.ec2.resources.annotation.Start;
import com.mprew.ec2.resources.annotation.Stop;
import com.mprew.ec2.resources.context.ResourceContext;
import com.mprew.ec2.resources.context.ResourceContextAware;
import com.mprew.ec2.resources.event.AwaitStateListener;
import com.mprew.ec2.resources.event.FilteredResourceListener;
import com.mprew.ec2.resources.event.ResourceActionListener;
import com.mprew.ec2.resources.event.ResourceEvent.EventType;
import com.mprew.ec2.resources.startup.DependencyElement;
import com.mprew.ec2.resources.validation.Validatable;
import com.mprew.ec2.resources.validation.ValidationException;

/**
 * The implementation of ResourceInfo and wrapper of all Resource-specific metadata, including resolved Method objects.
 * 
 * @author dgarson
 */
class ResourceMetadata implements Validatable, ResourceInfo {
	
	private static final Logger log = LoggerFactory.getLogger(ResourceMetadata.class);
	
	private String resourceName;
	private String beanName;
	private String[] declaredDependencies;
	private Object bean;
	private Class<?> beanClass;
	private Method initMethod;
	private Method startMethod;
	private Method stopMethod;
	private Method killMethod;
	private Method publishMethod;
	private Method pauseMethod;
	private Method resumeMethod;
	private Method resourceContextMethod;
	private AtomicReference<ResourceState> state = new AtomicReference<ResourceState>(ResourceState.INITIALIZING);
	private AtomicReference<ResourceHealth> health = new AtomicReference<ResourceHealth>(ResourceHealth.okHealth);
	private List<ResourceActionListener> actionListeners = new ArrayList<ResourceActionListener>();
	
	private Map<Field, ContextProperty> injectableFields = new HashMap<Field, ContextProperty>();
	private Set<Field> resourceFields = new HashSet<Field>();

	final Semaphore semaphore = new Semaphore(1);
	private DependencyElement element;
	
	ResourceMetadata(String resourceName, String beanName, Object bean, Method startMethod, Method stopMethod) {
		this.resourceName = resourceName;
		this.beanName = beanName;
		this.beanClass = bean.getClass();
		this.bean = bean;
		this.startMethod = startMethod;
		this.stopMethod = stopMethod;
		initialize();
	}
	
	public ResourceMetadata(String resourceName, String beanName, Object bean) {
		this.resourceName = resourceName;
		this.beanName = beanName;
		this.bean = bean;
		this.beanClass = bean.getClass();
		
		ReflectionUtils.doWithMethods(beanClass, new ReflectionUtils.MethodCallback(){
			@Override
			public void doWith(Method method) throws IllegalArgumentException {
				processMethod(method);
			}
		});
		initialize();
	}
	
	private void initialize() {
		ReflectionUtils.doWithFields(beanClass, new ReflectionUtils.FieldCallback(){
			@Override
			public void doWith(final Field field) throws IllegalArgumentException, IllegalAccessException {
				if (ResourceUtils.isResource(field.getType()) && field.isAnnotationPresent(ContextResource.class)) {
					resourceFields.add(field);
				}
				if (field.isAnnotationPresent(ContextProperty.class)) {
					try {
						ContextProperty annotation = field.getAnnotation(ContextProperty.class);
						AccessController.doPrivileged(new PrivilegedExceptionAction<Field>(){
							@Override
							public Field run() throws Exception {
								field.setAccessible(true);
								return field;
							}
						});
						injectableFields.put(field, annotation);
					} catch (PrivilegedActionException pae) {
						if (pae.getCause() instanceof IllegalAccessException) {
							throw (IllegalAccessException)pae.getCause();
						} else if (pae.getCause() instanceof IllegalArgumentException) {
							throw (IllegalArgumentException)pae.getCause();
						}
						throw new RuntimeException("Unable to make field accessible: " + field);
					}
				}
			}
		});
		preprocess();
	}
	
	public void addActionListener(ResourceActionListener listener) {
		synchronized (actionListeners) {
			if (!actionListeners.contains(listener)) {
				actionListeners.add(listener);
			}
		}
	}
	
	public void removeActionListener(ResourceActionListener listener) {
		synchronized (actionListeners) {
			actionListeners.remove(listener);
		}
	}
	
	@Override
	public Object getInstance() {
		return bean;
	}
	
	public Class<?> getBeanClass() {
		return beanClass;
	}
	
	@Override
	public List<String> getDependencyNames() {
		return Arrays.asList(declaredDependencies);
	}
	
	@Override
	public String getBeanName() {
		return beanName;
	}
	
	@Override
	public String getResourceName() {
		return resourceName;
	}
	
	public ResourceHealth getHealth() {
		return health.get();
	}
	
	void setHealth(ResourceHealth rh) {
		health.set(rh);
	}
	
	@Override
	public ResourceState getState() {
		return state.get();
	}
	
	/**
	 * Sets the state of this resource if it is not already in that state.
	 * @param newState the new state
	 * @return true if the state changed
	 */
	boolean setState(ResourceState newState) {
		if (state.get() == newState) {
			return false;
		}
		else {
			state.set(newState);
			return true;
		}
	}
	
	@Override
	public DependencyElement getElement() {
		return element;
	}
	
	/**
	 * Sets the element for this ResourceMetadata object, so there is a reference into the dependency tree.
	 * @param element the dependency element
	 */
	public void setElement(DependencyElement element) {
		this.element = element;
	}
	
	/**
	 * Preprocess the methods that we have discovered for this Resource. Also, discover dependencies that are declared on the Resource.
	 */
	private void preprocess() {
		if (stopMethod == null) {
			if (killMethod != null) {
				log.warn("Using @Kill method [" + killMethod.getName() + "] to substitute for undeclared @Stop for " + this);
				stopMethod = killMethod;
			}
		}
		
		Set<String> deps = new HashSet<String>();
		Dependencies depsAnnot = bean.getClass().getAnnotation(Dependencies.class);
		if (depsAnnot != null) {
			deps.addAll(Arrays.asList(depsAnnot.value()));
		}
		Annotation[] depAnnots = bean.getClass().getAnnotations();
		for (Annotation a : depAnnots) {
			if (a.annotationType() == Dependency.class) {
				Dependency dep = (Dependency)a;
				deps.add(dep.value());
			}
		}
		declaredDependencies = deps.toArray(new String[deps.size()]);
	}
	
	@Override
	public void validate() throws ValidationException {
		verifyMethod(startMethod, "Start");
		verifyMethod(stopMethod, "Stop");
		
		if (pauseMethod != null) {
			if (resumeMethod == null) {
				throw new ValidationException("Resume method is required if Pause method is specified for " + this);
			}
		}
		else if (resumeMethod != null) {
			throw new ValidationException("Resume method specified without the presence of a Pause method for " + this);
		}
	}
	
	private void verifyMethod(Method m, String name) throws ValidationException {
		if (m == null) {
			throw new ValidationException(name + " Method is required for " + this);
		}
	}
	
	/**
	 * Process a Reflective Method and depending on its annotations, record it and validate it as a resource action method.
	 * @param method the method
	 */
	private void processMethod(Method method) {
		if (method.getName().equals("setResourceContext") && ResourceContextAware.class.isInstance(bean) &&
			method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == ResourceContext.class) {
			resourceContextMethod = method;
			log.trace("Found setResourceContext method for " + this + " [ResourceContextAware]");
		}
		if (method.isAnnotationPresent(Initialize.class)) {
			verifyMethodParameters(method);
			initMethod = method;
			log.trace("Found @Initialize method for " + this + ": " + method.getName());
		}
		else if (method.isAnnotationPresent(Kill.class)) {
			verifyMethodParameters(method);
			killMethod = method;
			log.trace("Found @Kill method for " + this + ": " + method.getName());
		}
		else if (method.isAnnotationPresent(Pause.class)) {
			verifyMethodParameters(method);
			pauseMethod = method;
			log.trace("Found @Pause method for " + this + ": " + method.getName());
		}
		else if (method.isAnnotationPresent(Resume.class)) {
			verifyMethodParameters(method);
			resumeMethod = method;
			log.trace("Found @Resume method for " + this + ": " + method.getName());
		}
		else if (method.isAnnotationPresent(Start.class)) {
			verifyMethodParameters(method);
			startMethod = method;
			log.trace("Found @Start method for " + this + ": " + method.getName());
		}
		else if (method.isAnnotationPresent(Stop.class)) {
			verifyMethodParameters(method);
			stopMethod = method;
			log.trace("Found @Stop method for " + this + ": " + method.getName());
		}
		else if (method.isAnnotationPresent(Publish.class)) {
			verifyMethodParameters(method);
			publishMethod = method;
			log.trace("Found @Publish method for " + this + ": " + method.getName());
		}
	}
	
	private void verifyParameterType(Method method, Class<?> paramType) throws IllegalArgumentException {
		if (!ResourceInfo.class.isAssignableFrom(paramType) &&
			!ResourceContext.class.isAssignableFrom(paramType)) {
			throw new IllegalArgumentException("Invalid parameter type: " + paramType + " for resource method [" + method.getName() + "]");
		}
	}
	
	/**
	 * Verifies that the method provided only has either:<br>
	 * <ul>
	 * <li>a) one argument that is of type ResourceContext <b>or</b></li>
	 * <li>b) no arguments</li>
	 * </ul>
	 * Also, the method can throw the following types of exceptions: {@link ResourceException}, {@link RuntimeException}.
	 * Lastly, this also attempts to make the method <i>accessible</b> for manipulation through reflection.
	 * @param method the method to verify
	 */
	private void verifyMethodParameters(Method method) {
		Class<?>[] paramTypes = method.getParameterTypes();
		for (Class<?> paramType : paramTypes) {
			verifyParameterType(method, paramType);
		}
		if (paramTypes.length > 2) {
			throw new IllegalArgumentException("Invalid number of arguments for a resource action method: " + paramTypes.length + "; expected zero, one, or two");
		}
		Class<?>[] excTypes = method.getExceptionTypes();
		for (int i = 0; i < excTypes.length; i++) {
			if (!ResourceException.class.isAssignableFrom(excTypes[i]) &&
				!RuntimeException.class.isAssignableFrom(excTypes[i])) {
				throw new IllegalArgumentException("Invalid Exception declared for Resource Method [" + method.getName() + "]; only allows Runtime and ResourceException types for " + this);
			}
		}
		try {
			method.setAccessible(true);
		} catch (SecurityException se) {
			throw new IllegalArgumentException("Invalid visibility modifiers on method: " + method.getName() + " for " + this, se);
		}
	}
	
	/**
	 * Checks if this resource is context aware.
	 * @return true if context aware method has been discovered
	 */
	public boolean isContextAware() {
		return (resourceContextMethod != null);
	}
	
	@Override
	public boolean hasPublish() {
		return (publishMethod != null);
	}
	
	@Override
	public boolean hasPause() {
		return (pauseMethod != null);
	}
	
	public boolean hasKill() {
		return (killMethod != null);
	}

	public boolean hasInitialize() {
		return (initMethod != null);
	}
	
	public void setContextIfAware(ResourceContext ctx) throws ResourceException {
		if (resourceContextMethod != null) {
			invokeMethod(resourceContextMethod, ResourceAction.UNSPECIFIED, ctx);
		}
	}
	
	/**
	 * Initializes this resource by invoking its initialization method. If it does not have one, an exception is thrown.
	 * @param ctx the resource context
	 * @throws ResourceException on any exception or if there is no initialization method
	 */
	public void initialize(ResourceContext ctx) throws ResourceException {
		invokeMethod(initMethod, ResourceAction.INITIALIZING, ctx);
	}
	
	/**
	 * Kills this resource by invoking its kill method. If it does not have one, an exception is thrown.
	 * @param ctx the resource context
	 * @throws ResourceException on any exception or if there is no kill method
	 */
	public void kill(ResourceContext ctx) throws ResourceException {
		invokeMethod(killMethod, ResourceAction.SHUTTING_DOWN, ctx);
	}
	
	/**
	 * Starts this resource by invoking its start method. There is always a start method, otherwise <code>validate()</code> fails.
	 * @param ctx the resource context
	 * @throws ResourceException on any exception
	 */
	public void start(ResourceContext ctx) throws ResourceException {
		invokeMethod(startMethod, ResourceAction.STARTING, ctx);
	}
	
	/**
	 * Stops this resource by invoking its stop/shutdown method. There is always a stop method, otherwise <code>validate()</code> fails.
	 * @param ctx the resource context
	 * @throws ResourceException on any exception
	 */
	public void stop(ResourceContext ctx) throws ResourceException {
		invokeMethod(stopMethod, ResourceAction.STOPPING, ctx);
	}
	
	/**
	 * Publishes this resource by invoking its publish method. If it does not have one, an exception is thrown.
	 * @param ctx the resource context
	 * @throws ResourceException on any exception or if there is no publish method
	 */
	public void publish(ResourceContext ctx) throws ResourceException {
		invokeMethod(publishMethod, ResourceAction.PUBLISHING, ctx);
	}

	/**
	 * Pauses this resource by invoking its pause method. If it does not have one, an exception is thrown.
	 * @param ctx the resource context
	 * @throws ResourceException on any exception or if there is no pause method
	 */
	public void pause(ResourceContext ctx) throws ResourceException {
		invokeMethod(pauseMethod, ResourceAction.PAUSING, ctx);
	}
	
	/**
	 * Resumes this resource by invoking its resume method. If it does not have one, an exception is thrown.
	 * @param ctx the resource context
	 * @throws ResourceException on any exception or if there is no resume method
	 */
	public void resume(ResourceContext ctx) throws ResourceException {
		invokeMethod(resumeMethod, ResourceAction.RESUMING, ctx);
	}
	
	private Object[] getParameters(Method method, ResourceContext ctx) {
		Object[] params = new Object[method.getParameterTypes().length];
		int i = 0;
		for (Class<?> paramType : method.getParameterTypes()) {
			if (ResourceContext.class.isAssignableFrom(paramType)) {
				params[i++] = ctx;
			}
			else if (ResourceInfo.class.isAssignableFrom(paramType)) {
				params[i++] = this;
			}
			else {
				throw new IllegalArgumentException("Invalid parameter type: " + paramType + " for method [" + method.getName() + "]");
			}
		}
		return params;
	}
	
	private void invokeMethod(Method method, ResourceAction action, ResourceContext ctx) throws ResourceException {
		if (method == null) {
			throw new ResourceException("Unable to invoke " + action.getVerb() + " on " + this + " because it has no " + action.getVerb() + " method");
		}
		
		// Invoke before listeners
		synchronized (actionListeners) {
			for (ResourceActionListener listener : actionListeners) {
				listener.beforeResourceAction(this, action);
			}
		}
		
		try {
			method.invoke(bean, getParameters(method, ctx));
		} catch (InvocationTargetException ite) {
			if (ite.getTargetException() instanceof RuntimeException) {
				throw (RuntimeException)ite.getTargetException();
			}
			else if (ite.getTargetException() instanceof ResourceException) {
				throw (ResourceException)ite.getTargetException();
			}
			throw new ResourceException("Unable to " + action.getVerb() + " " + this, ite);
		} catch (IllegalAccessException iae) {
			throw new ResourceException("Unable to " + action.getVerb() + " " + this, iae);
		} catch (IllegalArgumentException iae) {
			throw new ResourceException("Invalid argument when invoking " + action.getVerb() + " on " + this, iae);
		}
		
		// Invoke after listeners
		synchronized (actionListeners) {
			for (ResourceActionListener listener : actionListeners) {
				listener.afterResourceAction(this, action);
			}
		}
	}
	
	/**
	 * Injects properties from the ResourceContext into this managed resource.
	 * @throws Exception if any exceptions occur injecting properties
	 */
	public void injectProperties(ResourceContext ctx, Map<Class<?>, Injections> injections) throws ResourceException {
		for (Map.Entry<Field, ContextProperty> entry : injectableFields.entrySet()) {
			Field field = entry.getKey();
			ContextProperty spec = entry.getValue();
			if (spec.state() == getState() || getState().getStabilizeState() == spec.state()) {
				Object value = null;
				if (!ctx.hasProperty(spec.name())) {
					try {
						value = ctx.getResource(spec.name()).getInstance();
					} catch (ResourceNotFoundException rnfe) {
						if (spec.required()) {
							throw new ResourceException("Missing injected property [" + spec.name() + "] for " + this, rnfe);
						}
						
						log.warn("Skipping injection of Property [" + spec.name() + "] because it is not in the Context");
						continue;
					}
				}
				else {
					value = ctx.getProperty(spec.name());
				}
				
				if (value != null) {
					try {
						Object currentValue = field.get(bean);
						if (currentValue == null) {
							field.set(bean, value);
							log.info("Successfully injected Property [" + spec.name() + "] into Bean [" + beanName + "] Field [" + field.getName() + "]");
						}
					} catch (IllegalAccessException iae) {
						throw new ResourceException("Unable to inject Bean [" + beanName + "] Field [" + field.getName() + "] with Property [" + spec.name() + "]", iae);
					} catch (IllegalArgumentException iae) {
						throw new ResourceException("Unable to inject Bean [" + beanName + "] Field [" + field.getName() + "] with Property [" + spec.name() + "]", iae);
					}
				}
			}
		}
		
		for (Iterator<Field> iter = resourceFields.iterator(); iter.hasNext();) {
			Field field = iter.next();
			String resName = field.getAnnotation(ContextResource.class).name();
			Class<?> resourceType = field.getType();
			try {
				field.setAccessible(true);
				Object currentValue = field.get(bean);
				if (currentValue == null) {
					ResourceInfo resource;
					if (StringUtils.isEmpty(resName)) {
						resource = ctx.getResource(resourceType);
					}
					else {
						resource = ctx.getResource(resName);
					}
					field.set(bean, resource.getInstance());
					log.info("Injected Field [" + field.getName() + "] for Resource [" + resource.getResourceName() + "] of Type [" + resourceType + "]");
				}
				iter.remove();
			} catch (IllegalAccessException iae) {
				throw new ResourceException("Unable to inject Field [" + field.getName() + "] for Resource [" + resourceName + "] for Resource Type [" + resourceType + "]", iae);
			} catch (IllegalArgumentException iae) {
				throw new ResourceException("Unable to inject Field [" + field.getName() + "] for Resource [" + resourceName + "] for Resource Type [" + resourceType + "]", iae);
			} catch (ResourceUniquenessException rue) {
				throw new ResourceException("Unable to inject Field [" + field.getName() + "] for Resource [" + resourceName + "] for Resource Type [" + resourceType + "]", rue);
			} catch (ResourceNotFoundException rnfe) {
				Injections injs;
				synchronized (injections) {
					injs = injections.get(resourceType);
					if (injs == null) {
						injs = new Injections(resourceType);
						injections.put(resourceType, injs);
					}
				}
				injs.addInjection(this, field);
			}
		}
	}
	
	@Override
	public boolean waitForState(ResourceState state) throws InterruptedException {
		EventType newState = EventType.forState(state);
		if (newState == EventType.UNKNOWN) {
			throw new IllegalArgumentException("Invalid target resource state: " + state);
		}
		final ResourceManager manager = ResourceManager.tlManager.get();
		final AwaitStateListener listener = new AwaitStateListener(this, newState) {
			@Override
			protected void done() {
				final FilteredResourceListener l = this;
				Thread t = new Thread(){
					@Override
					public void run() {
						try {
							Thread.sleep(100);
						} catch (InterruptedException ie) {
						} finally {
							manager.removeResourceListener(l, l);
						}
					}
				};
				t.start();
			}
		};
		manager.addResourceListener(listener, listener);
		return listener.awaitState();
	}
	
	@Override
	public String toString() {
		return "Resource[" + resourceName + "] State(" + state.get() + ")";
	}
}