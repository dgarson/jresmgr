package com.mprew.ec2.resources;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Interface that defines the contract for the basic resource manager operations.
 * 
 * @author dgarson
 */
public interface ResourceManagerOperations {
	/**
	 * Registers a resource with the resource manager. The resource must already exist in the wired application context.
	 * @param resName the resource name
	 * @param beanName the bean name
	 * @param startMethodName the start method name
	 * @param stopMethodName the stop method name
	 * @param autowire if the bean should be auto-wired
	 * @throws IllegalArgumentException if any method specified is invalid
	 */
	public void registerResource(String resName, String beanName, String startMethod, String stopMethod, boolean autowire);
	
	/**
	 * Initializes the specified resource if it is registered.
	 * @param resourceName the resource name
	 * @throws ResourceNotFoundException if the resource is not registered
	 * @throws ResourceException if any problems occur while pausing the resource
	 */
	// public void initialize(String resourceName) throws ResourceNotFoundException, ResourceException;
	
	/**
	 * Starts the specified resource if it is registered.
	 * @param resourceName the resource name
	 * @throws ResourceNotFoundException if the resource is not registered
	 * @throws ResourceException if any problems occur while starting the resource
	 */
	public void start(String resourceName) throws ResourceNotFoundException, ResourceException;
	
	/**
	 * Returns a job that will start the specified resource.
	 * @param resourceName the resourceName
	 * @return the job to start the resource
	 * @throws ResourceNotFoundException if the resource is not registered
	 */
	public Callable<?> startAsync(String resourceName) throws ResourceNotFoundException;
	
	/**
	 * Stops a resource either forcefully or gracefully.
	 * @param resourceName the resource name
	 * @param forceful if false, gracefully shut down the resource, otherwise if true, forcefully shut it down
	 * @throws ResourceNotFoundException if the resource is not registered
	 * @throws ResourceException if any problems occur while shutting down the resource
	 */
	public void stop(String resourceName, boolean forceful) throws ResourceNotFoundException, ResourceException;
	
	/**
	 * Returns a job that will stop the specified resource, forcefully or gracefully.
	 * @param resourceName the resourceName
	 * @param forceful if false, gracefully shut down the resource, otherwise if true, forcefully shut it down
	 * @return the job to stop the resource
	 * @throws ResourceNotFoundException if the resource is not registered
	 */
	public Callable<?> stopAsync(String resourceName, boolean forceful) throws ResourceNotFoundException;
	
	/**
	 * Pauses a resource if possible and it is registered.
	 * @param resourceName the resource name
	 * @throws ResourceNotFoundException if the resource is not registered
	 * @throws ResourceException if any problems occur while pausing the resource
	 */
	public void pause(String resourceName) throws ResourceNotFoundException, ResourceException;
	
	/**
	 * Returns a job that will pause the specified resource.
	 * @param resourceName the resourceName
	 * @return the job to pause the resource
	 * @throws ResourceNotFoundException if the resource is not registered
	 */
	public Callable<?> pauseAsync(String resourceName) throws ResourceNotFoundException, ImpossibleActionException;
	
	/**
	 * Resumes a resource if possible and it is registered.
	 * @param resourceName the resource name
	 * @throws ResourceNotFoundException if the resource is not registered
	 * @throws ResourceException if any problems occur while pausing the resource
	 */
	public void resume(String resourceName) throws ResourceNotFoundException, ResourceException;
	
	/**
	 * Returns a job that will resume the specified resource.
	 * @param resourceName the resourceName
	 * @return the job to resume the resource
	 * @throws ResourceNotFoundException if the resource is not registered
	 */
	public Callable<?> resumeAsync(String resourceName) throws ResourceNotFoundException, ImpossibleActionException;
	
	/**
	 * Publishes a resource if possible and it is registered.
	 * @param resourceName the resource name
	 * @throws ResourceNotFoundException if the resource is not registered
	 * @throws ResourceException if any problems occur while pausing the resource
	 */
	public void publish(String resourceName) throws ResourceNotFoundException, ResourceException;
	
	/**
	 * Returns a job that will publish the specified resource.
	 * @param resourceName the resourceName
	 * @return the job to publish the resource
	 * @throws ResourceNotFoundException if the resource is not registered
	 * @throws ImpossibleActionException if the resource cannot be published
	 */
	public Callable<?> publishAsync(String resourceName) throws ResourceNotFoundException, ImpossibleActionException;
	
	/**
	 * Waits until the specified resource is in the given state.
	 * @param resourceName the resource name
	 * @param state the resource state
	 * @return true if the resource entered the given state, false otherwise
	 * @throws InterruptedException if interrupted while waiting
	 * @throws ResourceNotFoundException if the resource is not registered
	 */
	public boolean waitForResourceState(String resourceName, ResourceState state) throws ResourceNotFoundException, InterruptedException;
	
	/**
	 * Waits for the resource manager to shut down.
	 * @throws InterruptedException if interrupted while waiting
	 */
	public void waitForShutdown() throws InterruptedException;
	
	/**
	 * Waits for one of the specified system states.
	 * @param states a variable number of resource states
	 * @return the state that the system entered
	 * @throws InterruptedException if interrupted while waiting
	 */
	public ResourceState waitForStates(ResourceState ... states) throws InterruptedException;
	
	/**
	 * Returns a future that will wait until a resource with the given
	 * name exists and will then return. If the resource manager is shutdown before the future
	 * terminates, then an InterruptedException will be thrown.
	 * @param resourceName the resource name
	 * @return a future for the specified resource
	 * 
	 * @param <T> the type of the resource
	 */
	public <T> Future<T> futureForResource(String resourceName);
	
	/**
	 * Returns a future that will wait until a resource with the given
	 * name exists and is in the specified state. If the resource manager is
	 * shutdown before the future terminates, then an InterruptedException will be thrown.
	 * @param resourceName the resource name
	 * @param state the state to wait for
	 * @return a future for the specified resource
	 * 
	 * @param <T> the type of the resource
	 */
	public <T> Future<T> futureForResource(String resourceName, ResourceState state);
}
