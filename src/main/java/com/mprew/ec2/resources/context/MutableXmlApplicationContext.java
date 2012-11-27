package com.mprew.ec2.resources.context;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * A mutable XML Application Context that allows arbitrary bean definitions during runtime.
 * 
 * @author dgarson
 */
public class MutableXmlApplicationContext extends ClassPathXmlApplicationContext implements MutableApplicationContext {
	
	public MutableXmlApplicationContext(String ... locations) {
		super(locations);
	}
	
	public MutableXmlApplicationContext() {
		super();
	}
	
	@Override
	public void registerBean(String beanName, Object bean) {
		getBeanFactory().registerSingleton(beanName, bean);
	}
}