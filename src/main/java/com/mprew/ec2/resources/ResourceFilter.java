package com.mprew.ec2.resources;

/**
 * Defines the contract for a filter that makes determinations on a per-resource basis as to whether or not
 * the resource is a match for the filter implementation.
 * 
 * @author dgarson
 */
public interface ResourceFilter {
	
	/** A static instance of a ResourceFilter that accepts all resources */
	public static ResourceFilter ACCEPT_EVERYTHING = new ResourceFilter() {
		@Override
		public boolean accepts(ResourceInfo info) {
			return true;
		}
	};
	
	/**
	 * Checks if this filter accepts the information specified by the provided resource.
	 * @param info the resource information
	 * @return true if accepted, false otherwise
	 */
	public boolean accepts(ResourceInfo info);
}