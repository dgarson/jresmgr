package com.mprew.ec2.resources.event;

import com.mprew.ec2.resources.ResourceFilter;

/**
 * A special subtype of a ResourceListener that also implements a ResourceFilter itself.
 * 
 * @author dgarson 
 */
public interface FilteredResourceListener extends ResourceFilter, ResourceListener {
}