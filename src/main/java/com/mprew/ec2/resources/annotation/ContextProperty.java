package com.mprew.ec2.resources.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.mprew.ec2.resources.ResourceState;

/**
 * This is used to look up a contextual property from the ResourceContext that is being used to
 * manage the object on which this annotation is present. If an injection is specified using this
 * annotation and it is not explicitly declared as optional, then the absence of such a property in
 * the ResourceContext will result in an exception being through during initialization.
 * 
 * @author dgarson
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ContextProperty {
	
	/**
	 * The property name.
	 */
	String name();
	
	/**
	 * The resource state to inject the property.
	 */
	ResourceState state() default ResourceState.INITIALIZED;
	
	/**
	 * Whether or not the property injection is required. Defaults to true.
	 */
	boolean required() default true;
}