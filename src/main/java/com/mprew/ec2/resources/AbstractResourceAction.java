package com.mprew.ec2.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mprew.ec2.resources.startup.DependencyConditionException;
import com.mprew.ec2.resources.startup.DependencyException;
import com.mprew.ec2.resources.validation.Validatable;
import com.mprew.ec2.resources.validation.ValidationException;

/**
 * The abstract superclass for all actions that apply to a collection of resources. This class provides a good deal of utility methods for dealing
 * with such resource collections, as well as logic that can help determine which resources become startable/stoppable after others have finished
 * performing the same action. This class is designed to organize resource actions into stages so that they can be done concurrently using an underlying
 * Executor.
 * 
 * @author dgarson
 */
public abstract class AbstractResourceAction implements Callable<Void>, Validatable
{	
	private static final int STATE_INITIALIZED = 0;
	private static final int STATE_RUNNING = 1;
	private static final int STATE_FINISHED = 2;
	
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	private final Collection<? extends ResourceInfo> initialResources;
	protected final List<ResourceInfo> resources;
	protected final Map<ResourceInfo, ResourceFailureTracker> resourceFailures = new HashMap<ResourceInfo, ResourceFailureTracker>();
	private final LinkedBlockingQueue<Future<Boolean>> completionQueue;
	private final ExecutorCompletionService<Boolean> startupService;
	private int started = 0;
	private int finished = 0;
	protected final String noun;
	protected final String verb;
	protected final String ptVerb;
	private final Map<Future<Boolean>, ResourceInfo> jobMap = new HashMap<Future<Boolean>, ResourceInfo>();
	
	protected final ResourceManager resourceManager;
	protected final boolean isPhase;
	
	private final AtomicInteger state = new AtomicInteger(STATE_INITIALIZED);
	private final CountDownLatch latch = new CountDownLatch(1);
	
	/**
	 * Constructs a new resource action with a given ResourceManager and a set of resources.
	 * @param manager the resource manager
	 * @param resources the resource collection
	 */
	protected AbstractResourceAction(ResourceManager manager, Collection<? extends ResourceInfo> resources, boolean isPhase) {
		this.resourceManager = manager;
		this.isPhase = isPhase;
		this.initialResources = resources;
		this.resources = new LinkedList<ResourceInfo>();
		completionQueue = new LinkedBlockingQueue<Future<Boolean>>(resources.size());
		startupService = new ExecutorCompletionService<Boolean>(manager.getJobExecutor(), completionQueue);
		
		noun = getActionName(true);
		verb = getActionName(false);
		ptVerb = ResourceUtils.pastTense(verb);
	}
	
	/**
	 * Populates the list of resources, optionally skipping any that this task is not applicable to.
	 */
	private void populateResources() {
		for (ResourceInfo resource : initialResources) {
			if (isApplicable(resource)) {
				resources.add(resource);
			}
			else {
				skippedResource(resource);
			}
		}
	}
	
	/**
	 * Changes the state of a resource by using the package-private method in the ResourceManager instance.
	 * @param resource the resource to change
	 * @param currentState the current resource state
	 * @param newState the new resource state to transition to
	 * @see ResourceManager#changeResourceState(ResourceMetadata, ResourceState, ResourceState)
	 */
	protected ResourceException changeResourceState(ResourceInfo resource, ResourceState currentState, ResourceState newState, boolean updateSystemHealth) {
		return resourceManager.changeResourceState(resource, currentState, newState, updateSystemHealth);
	}
	
	/**
	 * Changes the system state of the ResourceManager if appropriate.
	 * @param newState the new state
	 */
	protected void changeSystemState(ResourceState newState) {
		if (resourceManager.getSystemState().isNewStateOk(newState)) {
			resourceManager.changeSystemState(newState);
		}
	}
	
	/**
	 * Called whenever the execution of this action is beginning.
	 */
	protected void beginningAction() {
		// No-op
	}
	
	/**
	 * Called whenever the execution of this action finishes, in success or error.
	 */
	protected void finishedAction() {
		// No-op
	}
	
	@SuppressWarnings("unused")
	@Override
	public void validate() throws ValidationException {
		// No-op
	}
	
	/**
	 * Gets the name of the action being performed, in the form specified.
	 * @param isNoun if the noun form should be returned, otherwise return the verb
	 * @return the action name in the specified form
	 */
	public abstract String getActionName(boolean isNoun);
	
	/**
	 * Checks if this action is even applicable to the specified resource. If it is not, the resource is
	 * not added to the internal collection of resources to which this action will be applied.
	 * @param resource the resource to check
	 * @return true if valid for this action, false otherwise
	 */
	protected abstract boolean isApplicable(ResourceInfo resource);
	
	/**
	 * Callback that is invoked whenever a resource has been skipped due to a false return from
	 * <code>isApplicable</code>.
	 * @param skipped the skipped resource
	 */
	protected void skippedResource(ResourceInfo skipped) {
		// Default is no-op
	}
	
	/**
	 * Checks if we are allowed to submit the subtask for the given resource.
	 * @param metadata the resource to check against
	 * @return true if submittable, false otherwise
	 * @throws DependencyException any exceptions while checking submitability
	 */
	protected abstract boolean canSubmit(ResourceInfo metadata) throws DependencyConditionException;
	
	/**
	 * Creates the job to invoke on the resource that is allowed to be submitted.
	 * @param resource the resource to submit
	 * @return the Callable for this job
	 */
	protected abstract Callable<Boolean> createJob(ResourceInfo resource);
	
	/**
	 * Submits an out-of-band job for a resource. This can be used to submit jobs that will still be monitored for completion, but are not
	 * applying the standard action for this task.
	 * @param job the job
	 * @param resource the resource the job applies to
	 */
	protected void submitJob(Callable<Boolean> job, ResourceInfo resource) {
		Future<Boolean> future = startupService.submit(job);
		if (future == null) {
			log.error("Unable to submit job!! Future is null??");
		}
		else {
			jobMap.put(future, resource);
			started++;
		}
	}
	
	/**
	 * Waits until we are allowed to submit the next job.
	 * @param jobMap a mapping from Future to ResourceMetadata
	 * @throws InterruptedException if interrupted while waiting
	 * @return false if we should stop submitting jobs, true if we should continue
	 */
	protected abstract boolean waitToSubmit(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException;
	
	/**
	 * Waits until at least one job has been successfully completed before returning.
	 * @param jobMap the job mapping
	 * @throws InterruptedException if interrupted while waiting
	 */
	protected boolean waitForFirstCompletion(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException {
		// Wait until at least one of the jobs finished or all failed
		Future<Boolean> future;
		try {
			while (hasMoreJobs() && ((future = takeJob()) != null)) {
				Boolean result;
				try {
					result = future.get();
				} catch (ExecutionException ee) {
					log.error("Unable to " + verb + " Resource[" + jobMap.get(future).getResourceName() + "]", ee.getCause());
					result = Boolean.FALSE;
				} catch (InterruptedException ie) {
					// ignored - should never occur because Future must already be done!
					log.warn("Interrupted while waiting for future to complete!");
					result = Boolean.FALSE;
				}
				
				if (result.booleanValue()) {
					log.trace("Found a successfully " + ptVerb + " resource: " + jobMap.get(future) + "; continuing " + noun + " job submission");
					return true;
				}
				else {
					log.warn("Found a resource that failed to " + verb + ": " + jobMap.get(future));
				}
			}
			log.warn("Did not find any more jobs completed; returning true after sleeping 250ms");
			Thread.sleep(250);
			return true;
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			log.warn("Interrupted while waiting for future(s) to finish for " + noun + " tasks", ie);
			throw ie;
		}
	}
	
	/**
	 * Gets the name of the thread for this action.
	 * @return the thread name
	 */
	public String getThreadName() {
		return "Resource" + getActionName(false) + "Thread";
	}
	
	/**
	 * Checks if we have more jobs to submit.
	 * @return true if more jobs have yet to be submitted
	 */
	public boolean moreToSubmit() {
		return !resources.isEmpty();
	}
	
	/**
	 * Checks if there is another job to be completed.
	 * @return true if more unfinished jobs exist, false otherwise
	 */
	public boolean hasMoreJobs() {
		return (finished < started);
	}
	
	/**
	 * Gets the next job Future from the completion queue. If there is not one yet completed, then this will block until
	 * another one has finished.
	 * @return the next completed job Future
	 * @throws InterruptedException if interrupted while waiting
	 */
	protected Future<Boolean> takeJob() throws InterruptedException {
		Future<Boolean> job = completionQueue.take();
		finished++;
		ResourceInfo resource = jobMap.get(job);
		if (resource != null) {
			resourceManager.finishedWith(resource);
		}
		return job;
	}
	
	/**
	 * Waits for all jobs to complete, successfully or in error.
	 * @throws InterruptedException if interrupted while waiting
	 */
	protected void waitUntilFinished(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException {
		Future<Boolean> future;
		while (hasMoreJobs() && ((future = takeJob()) != null)) {
			Boolean result;
			try {
				result = future.get();
			} catch (ExecutionException ee) {
				log.error("Unable to " + verb + " " + jobMap.get(future), ee);
				result = Boolean.FALSE;
			} catch (InterruptedException ie) {
				// ignored - should never occur because Future must already be done!
				result = Boolean.FALSE;
			}
			
			if (result.booleanValue()) {
				log.trace("Found a successfully " + ptVerb + " resource: " + jobMap.get(future) + "; continuing " + noun + " job submission");
			}
			else {
				log.warn("Found a resource that failed to " + verb + ": " + jobMap.get(future));
			}
		}
	}
	
	@Override
	public Void call() throws Exception {
		try {
			state.set(STATE_RUNNING);
			populateResources();
			
			for (ResourceInfo resource : resources) {
				resourceManager.workingOn(resource);
			}
			
			try {
				validate();
			} catch (ValidationException ve) {
				if (ve.getCause() != null && ve.getCause() instanceof Exception) {
					throw (Exception)ve.getCause();
				}
				throw new ResourceException("Unable to perform resource action", ve);
			}
			
			// Invoke callback if overridden
			beginningAction();
			
			// 1) We want to submit all of the resources that are presently stoppable
			// 2) We want to wait for one or more of those futures to finish successfully
			// 2a) Next, we want to filter through the list again for resources that can NOW be shut down
			// 3) Continue this loop until all resources are shut down
			Set<ResourceInfo> continuableResources = new HashSet<ResourceInfo>();
			
			while (!resources.isEmpty()) {
				for (ListIterator<ResourceInfo> iter = resources.listIterator(); iter.hasNext();) {
					ResourceInfo resource = iter.next();
					try {
						if (canSubmit(resource)) {
							continuableResources.add(resource);
							iter.remove();
						}
					} catch (DependencyConditionException dce) {
						if (!resourceManager.isWorkingOn(dce.getResource())) {
							ResourceFailureTracker tracker = resourceFailures.get(resource);
							if (tracker == null) {
								tracker = new ResourceFailureTracker(resource);
								resourceFailures.put(resource, tracker);
							}
							tracker.failure(dce);
						}
						else {
							log.warn("Ignoring to determine submission of Resource [" + resource.getResourceName() + "] for Resource [" + dce.getResource().getResourceName() + "] due to it being worked");
						}
					}
				}
				
				// Accumulate a list of resources that we can continue to perform our action on
				List<String> resourceNames = new ArrayList<String>();
				for (final ResourceInfo resource : continuableResources) {
					// Submit a job for that resource and map its Future to the resource
					jobMap.put(startupService.submit(createJob(resource)), resource);
					resourceNames.add(resource.getResourceName());
					
					started++;
				}
				if (!resourceNames.isEmpty()) {
					log.info("Submitting job to " + verb + " resources: " + resourceNames);
				}
				else {
					log.info("Waiting another cycle to submit the remaining " + resources.size() + " resources");
				}
				
				// Clear the startable resources as we have already tried to stop them
				continuableResources.clear();
				
				// Check if we have more jobs to submit
				if (moreToSubmit()) {
					try {
						// Wait until we can submit another job
						waitToSubmit(jobMap);
						
						// Check if we have failed
						ResourceFailureTracker tracker = getIfFailuresExceededThreshold(5);
						if (tracker != null) {
							throw new ResourceException("Unable to complete action " + getActionName(false) + " for " + resources, tracker.getFailureException());
						}
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						log.warn("Interrupted while waiting to submit next job!", ie);
						break;
					}
				}
			}
			
			try {
				waitUntilFinished(jobMap);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				log.warn("Interrupted while waiting for future(s) to finish for " + noun + " tasks", ie);
			}
			
			return null;
		} finally {
			state.set(STATE_FINISHED);
			latch.countDown();
			
			// Invoke callback if overridden
			finishedAction();
		}
	}
	
	/**
	 * Checks if the failures for the resources in this action have exceeded the provided threshold.
	 * @param threshold the failure threshold
	 * @return true if exceeded by any resource
	 */
	public ResourceFailureTracker getIfFailuresExceededThreshold(int threshold) {
		for (Map.Entry<ResourceInfo, ResourceFailureTracker> entry : resourceFailures.entrySet()) {
			if (entry.getValue().getFailureCount() > threshold) {
				log.info("Resource [" + entry.getKey() + "] has failed to perform a state change " + entry.getValue().getFailureCount() + " times");
				return entry.getValue();
			}
		}
		return null;
	}
	
	/**
	 * Waits until this action has completed its execution.
	 * @throws InterruptedException if interrupted while waiting
	 * @throws IllegalStateException if the action has not yet been started
	 */
	public void waitFor() throws InterruptedException {
		if (state.get() == STATE_INITIALIZED) {
			throw new IllegalStateException("You must wait until the action has been started before waiting on it");
		}
		latch.await();
	}
	
	/**
	 * Waits up until the specified timeout for this action to complete its execution.
	 * @param timeout the amount of time
	 * @param unit the time unit
	 * @return false if timeout was reached, true if the action finished (or was already finished)
	 * @throws InterruptedException if interrupted while waiting
	 * @throws IllegalStateException if the action has not yet been started
	 */
	public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
		if (state.get() == STATE_INITIALIZED) {
			throw new IllegalStateException("You must wait until the action has been started before waiting on it");
		}
		return latch.await(timeout, unit);
	}
	
	protected static class ResourceFailureTracker {
		private ResourceInfo resource;
		private int failureCount;
		private long lastFailureTime;
		private List<Exception> failureCauses = new ArrayList<Exception>();
		
		public ResourceFailureTracker(ResourceInfo resource) {
			this.resource = resource;
		}
		
		public ResourceInfo getResource() {
			return resource;
		}
		
		public int getFailureCount() {
			return failureCount;
		}
		
		public long getLastFailureTime() {
			return lastFailureTime;
		}
		
		public void failure(Exception cause) {
			failureCount++;
			lastFailureTime = System.currentTimeMillis();
			if (cause != null) {
				failureCauses.add(cause);
			}
		}
		
		public void failure() {
			failure(null);
		}
		
		public Exception getFailureException() {
			if (failureCauses.isEmpty()) {
				return null;
			}
			return failureCauses.get(0);
		}
	}
}