package com.mprew.ec2.resources;

import org.springframework.beans.BeansException;

/**
 * Exception that is thrown whenever the dependencies that have been declared for a resource are not valid.
 * 
 * @author dgarson
 */
public class DependencyDeclarationException extends BeansException {
	
	private static final long serialVersionUID = 6508260290063384398L;

	public DependencyDeclarationException(String message, Throwable cause) {
		super(message, cause);
	}
}