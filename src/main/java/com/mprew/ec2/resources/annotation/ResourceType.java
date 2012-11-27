package com.mprew.ec2.resources.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceType {
	
	/**
	 * The name of the reason type. This must be unique across discoverable resources.
	 * @return the resource name
	 */
	String name();
	
	/**
	 * Declare the dependencies of this resource.
	 * @return the array of resource names
	 */
	// String[] dependencies();
}
