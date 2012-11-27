package com.mprew.ec2.resources.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Dependencies annotation is used to declare the dependencies of a resource so that it can be started in the correct sequence. This
 * is also used to ensure that shutdown occurs in the correct order, ensuring that dependent resources are available at the point of 
 * shutdown of the resource this dependency is declared on.
 * 
 * @author dgarson
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Dependencies {
	
	/**
	 * Any number of dependency names.
	 */
	String[] value();
}
