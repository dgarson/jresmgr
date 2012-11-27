package com.mprew.ec2.resources;

import java.lang.reflect.Method;

import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.SynchronousInvocation;

/**
 * Static resource utility methods.
 * 
 * @author dgarson
 */
public class ResourceUtils {
	
	/**
	 * Checks if the specified class is a resource class.
	 * @param type the resource class
	 * @return true if a resource, false otherwise
	 */
	public static boolean isResource(Class<?> type) {
		return type.isAnnotationPresent(ResourceType.class);
	}
	
	/**
	 * Checks if the specified ResourceListener should be synchronously invoked.
	 * @param listener the listener instance
	 * @return true if synchronously invoked, false for asynchronous invocation
	 */
	public static boolean isSynchronous(Object listener) {
		return listener.getClass().isAnnotationPresent(SynchronousInvocation.class);
	}
	
	/**
	 * Finds a valid method that matches the priority-descending array of argument types that are to be considered valid.
	 * @param methodClass the method class
	 * @param methodName the method name
	 * @param argTypes a 2d array where the 2nd dimension is the argument types
	 * @return the method that was found
	 * @throws NoSuchMethodException if the method could not be found
	 */
	public static Method findMethod(Class<?> methodClass, String methodName, Class<?>[][] argTypes) throws NoSuchMethodException {
		try {
			for (int i = 0; i < argTypes.length; i++) {
				try {
					Method method = methodClass.getDeclaredMethod(methodName, argTypes[i]);
					return method;
				} catch (NoSuchMethodException nsme) {
					continue;
				}
			}
		} catch (Exception e) {
			throw new NoSuchMethodException("Unable to find method [" + methodName + "] with valid arg types: " + e.getMessage());
		}
		throw new NoSuchMethodException(methodName);
	}
}