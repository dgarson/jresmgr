package com.mprew.ec2.resources;

import java.util.Collection;

import com.mprew.ec2.resources.startup.DependencyElement;

/**
 * Interface defining the accessor methods for a Resource instance.
 * 
 * @author dgarson
 */
public interface ResourceInfo {
	
	/**
	 * Gets the proper name of this resource.
	 * @return the resource name
	 */
	public String getResourceName();
	
	/**
	 * Gets the name of the bean declaration of this resource.
	 * @return the bean name
	 */
	public String getBeanName();
	
	/**
	 * Gets the object instance that is the managed resource.
	 * @return the bean instance
	 */
	public Object getInstance();
	
	/**
	 * Gets the state of this resource.
	 * @return the resource state
	 */
	public ResourceState getState();
	
	/**
	 * Gets the DependencyElement for this Resource.
	 * @return the dependency element
	 */
	public DependencyElement getElement();
	
	/**
	 * Gets the names of the dependencies of this resource.
	 * @return the dependency names
	 */
	public Collection<String> getDependencyNames();
	
	/**
	 * Checks if this resource has an optional publish method.
	 * @return true if it has a publish, false otherwise
	 */
	public boolean hasPublish();
	
	/**
	 * Checks if this resource has an optional pause method.
	 * @return true if it has a pause, false otherwise
	 */
	public boolean hasPause();
	
	/**
	 * Waits for a specified state.
	 * @param state the state to wait for
	 * @return true if the state was entered, false if it couldn't be
	 * @throws InterruptedException if interrupted while waiting
	 */
	public boolean waitForState(ResourceState state) throws InterruptedException;
}