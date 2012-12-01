package com.mprew.ec2.resources;

import java.lang.reflect.Method;
import java.util.Collection;

import com.mprew.ec2.resources.action.ResourceAction;
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
	 * Waits for a specified state.
	 * @param state the state to wait for
	 * @return true if the state was entered, false if it couldn't be
	 * @throws InterruptedException if interrupted while waiting
	 */
	public boolean waitForState(ResourceState state) throws InterruptedException;
	
	/**
	 * Gets the resource method for the specified action, if one exists.
	 * @param action the action type
	 * @return the resource method, or <code>null</code>
	 */
	public Method getResourceMethod(ResourceAction action);
}