package com.mprew.ec2.resources.context;

import java.util.concurrent.Future;

import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.ResourceManager;
import com.mprew.ec2.resources.ResourceManagerOperations;
import com.mprew.ec2.resources.ResourceNotFoundException;
import com.mprew.ec2.resources.ResourceState;
import com.mprew.ec2.resources.ResourceUniquenessException;

/**
 * The public context in which a Resource exists.
 * 
 * @author dgarson
 * @see ResourceManager
 */
public interface ResourceContext {
	
	/**
	 * The Thread-Local ResourceContext instance.
	 */
	public static final ThreadLocal<ResourceContext> CONTEXT = new ThreadLocal<ResourceContext>();
	
	/**
	 * Gets a property from the resource context.
	 * @param key the property key
	 * @return the property value, or <code>null</code>
	 */
	public Object getProperty(String key);
	
	/**
	 * Checks if the specified property exists in this context.
	 * @param key the property key
	 * @return true if it exists, false otherwise
	 */
	public boolean hasProperty(String key);
	
	/**
	 * Sets a property on this resource context.
	 * @param key the property key
	 * @param value the property value
	 */
	public void setProperty(String key, Object value);
	
	/**
	 * Removes a property by name and returns its value.
	 * @param key the property key
	 * @return the property value, or <code>null</code> if it did not exist
	 */
	public Object removeProperty(String key);
	
	/**
	 * Gets the operations that can be performed on the manager of this context.
	 * @return the manager operations
	 */
	public ResourceManagerOperations getOperations();
	
	/**
	 * Gets a singleton resource of the given resource type, if one exists. If the resource does not exist, then an appropriate exception is thrown.
	 * If there are more than one resources with the given resource type, then a ResourceUniquenessException is thrown.
	 * @param resourceClass the resource class
	 * @return the resource information
	 * @throws ResourceNotFoundException if the resource could not be found
	 * @throws ResourceUniquenessException if there are multiple resources with the given type
	 */
	public ResourceInfo getResource(Class<?> resourceClass) throws ResourceNotFoundException, ResourceUniquenessException;
	
	/**
	 * Gets a resource by its proper name, if one exists. If no resource exists with the specified name then an exception is thrown.
	 * @param resourceName the resource name
	 * @return the resource information
	 * @throws ResourceNotFoundException if the resource could not be found
	 */
	public ResourceInfo getResource(String resourceName) throws ResourceNotFoundException;
	
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
