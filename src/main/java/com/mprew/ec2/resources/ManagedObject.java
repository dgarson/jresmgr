package com.mprew.ec2.resources;

/**
 * Defines a contract for objects that are designed to be managed by the ResourceManager without actually being resources. They can be pushed into the ResourceManager
 * and injected into Resources but they do not require the special annotated resource methods and cannot be used as a dependency for a resource.
 * 
 * @author dgarson
 */
public interface ManagedObject {
	
	/**
	 * Gets the resource name for this managed object.
	 * @return the resource name
	 */
	public String getResourceName();
}