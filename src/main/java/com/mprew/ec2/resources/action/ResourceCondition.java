package com.mprew.ec2.resources.action;

import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.startup.DependencyException;

/**
 * An abstraction over a condition that can be evaluated for any resource.
 * 
 * @author dgarson
 */
public interface ResourceCondition {
	
	/**
	 * Evaluates this condition.
	 * @param resource the resource
	 * @return true if condition is satisfied, false otherwise
	 */
	public boolean evaluate(ResourceInfo resource) throws DependencyException;
}
