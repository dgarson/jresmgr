package com.mprew.ec2.resources.context;

import org.springframework.context.ApplicationContext;

/**
 * A MutableApplicationContext is capable of publishing beans after startup.
 * 
 * @author dgarson
 */
public interface MutableApplicationContext extends ApplicationContext {
	
	/**
	 * Registers a bean with the application context.
	 * @param beanName the bean name
	 * @param bean the bean instance
	 * @throws IllegalArgumentException if the bean name is in use, or the bean instance is null
	 */
	public void registerBean(String beanName, Object bean);
}