package com.mprew.ec2.resources.context;

/**
 * Interface used for Resource implementations who wish to have consistent access to their ResourceContext.
 * 
 * @author dgarson
 */
public interface ResourceContextAware {
	
	/**
	 * Sets the ResourceContext after the properties on the ResourceManager have been set.
	 * @param context the resource context
	 */
	public void setResourceContext(ResourceContext context);
}