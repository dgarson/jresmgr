package com.mprew.ec2.resources.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that declares that a listener should be synchronously invoked as opposed to having its methods forked onto a dispatch thread.
 * 
 * @author dgarson
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SynchronousInvocation {
}