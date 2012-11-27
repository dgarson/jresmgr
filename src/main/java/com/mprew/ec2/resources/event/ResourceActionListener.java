package com.mprew.ec2.resources.event;

import com.mprew.ec2.resources.action.ResourceAction;

import com.mprew.ec2.resources.ResourceInfo;

/**
 * A listener for Resource-specific action invocations.
 * 
 * @author dgarson
 */
public interface ResourceActionListener {
	
	/**
	  * Invoked before the invocation of the resource action associated with the specified <code>action</code>.
	  * @param resource the resource
	  * @param action the resource action type
	  */
	 public void beforeResourceAction(ResourceInfo resource, ResourceAction action);
	 
	 /**
	  * Invoked after the invocation of the resource action associated with the specified <code>action</code>.
	  * @param resource the resource
	  * @param action the resource action type
	  */
	 public void afterResourceAction(ResourceInfo resource, ResourceAction action);
}