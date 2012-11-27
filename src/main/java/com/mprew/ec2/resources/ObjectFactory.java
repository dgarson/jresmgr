package com.mprew.ec2.resources;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * The ObjectFactory class provides a static utility method for constructing new Managed beans after the system has started up. Any object
 * created through the ObjectFactory is automatically registered with the ResourceManager as a managed resource.
 * 
 * @author dgarson
 */
public class ObjectFactory {
	
	static ResourceManager resourceManager;
	
	/**
	 * Constructs a new ManagedResource object of the specified type with the given resource name. It is automatically registered with the
	 * ResourceManager. 
	 * @param name the resource name
	 * @param instanceType the resource instance type
	 * @param paramTypes the constructor parameter types
	 * @param args the constructor arguments
	 * @return the new object instance
	 * @throws ResourceException if any exceptions occur while constructing the object
	 */
	public static <T> T newInstance(String name, Class<T> instanceType, Class<?>[] paramTypes, Object ... args) throws ResourceException {
		if (resourceManager == null) {
			throw new ResourceException("ObjectFactory used prior to completion of ApplicationContext initialization!");
		}
		try {
			Constructor<T> constructor = instanceType.getDeclaredConstructor(paramTypes);
			T instance = constructor.newInstance(args);
			resourceManager.registerResource(name, toBeanName(name), instance, true, true);
			return instance;
		} catch (IllegalAccessException iae) {
			throw new ResourceException("Unable to access constructor", iae);
		} catch (InstantiationException ie) {
			throw new ResourceException("Unable to instantiate class " + instanceType, ie);
		} catch (IllegalArgumentException iae) {
			throw new ResourceException("Illegal constructor argument", iae);
		} catch (InvocationTargetException ite) {
			throw new ResourceException("Exception thrown by constructor of " + instanceType, ite.getTargetException());
		} catch (NoSuchMethodException nsme) {
			throw new ResourceException("No constructors exist matching the specified signature [length " + paramTypes.length + "] in [" + instanceType + "]", nsme);
		}
	}
	
	/**
	 * Constructs a new ManagedResource object of the specified type with the given resource name but without any constructor arguments.
	 * This is a prime use case for taking advantage of the @ContextProperty injection of resources.
	 * @param name the resource name
	 * @param instanceType the instance type
	 * @return the new object instance
	 * @throws ResourceException if any exceptions occur while constructing the object
	 */
	public static <T> T newInstance(String name, Class<T> instanceType) throws ResourceException {
		if (resourceManager == null) {
			throw new ResourceException("ObjectFactory used prior to completion of ApplicationContext initialization!");
		}
		try {
			Constructor<T> constructor = instanceType.getDeclaredConstructor();
			T instance = constructor.newInstance();
			resourceManager.registerResource(name, toBeanName(name), instance, true, true);
			return instance;
		} catch (IllegalAccessException iae) {
			throw new ResourceException("Unable to access constructor", iae);
		} catch (InstantiationException ie) {
			throw new ResourceException("Unable to instantiate class " + instanceType, ie);
		} catch (IllegalArgumentException iae) {
			throw new ResourceException("Illegal constructor argument", iae);
		} catch (InvocationTargetException ite) {
			throw new ResourceException("Exception thrown by constructor of " + instanceType, ite.getTargetException());
		} catch (NoSuchMethodException nsme) {
			throw new ResourceException("No constructors exist matching the specified signature [length 0] in [" + instanceType + "]", nsme);
		}
	}

	/**
	 * Converts a resource name into a bean name in a standardized way.
	 * @param resourceName the resource name
	 * @return the bean name
	 */
	public static final String toBeanName(String resourceName) {
		return "objfactory_bean_" + resourceName;
	}
}