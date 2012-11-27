package com.mprew.ec2.resources;

/**
 * A ResourceException is thrown whenever an unexpected condition is encountered in the management of resources.
 * 
 * @author dgarson
 */
public class ResourceException extends Exception {
	private static final long serialVersionUID = -355575251217876852L;

	public ResourceException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public ResourceException(String message) {
		super(message);
	}
}
