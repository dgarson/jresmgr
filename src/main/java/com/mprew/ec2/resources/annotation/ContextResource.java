package com.mprew.ec2.resources.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for declaring an automatic dependency for discovery of a unique resource
 * with the type of the annotated field. If there is not a <b>unique</b> resource of the field's
 * type in the context, then if no resources can be found, an exception will be thrown.
 * 
 * @author dgarson
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ContextResource {
	/**
	 * The name of the resource. Optional. If left unspecified, the resource type must have a unique
	 * bean registered of its type.
	 * @return the resource name (optional)
	 */
	String name() default "";
}