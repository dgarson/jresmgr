package com.mprew.ec2.resources.startup;

import com.mprew.ec2.resources.ResourceException;

public class DependencyException extends ResourceException {
	
	private static final long serialVersionUID = -4961485244323373523L;

	public DependencyException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public DependencyException(String message) {
		super(message);
	}
}