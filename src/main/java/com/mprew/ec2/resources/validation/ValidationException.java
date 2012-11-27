package com.mprew.ec2.resources.validation;

public class ValidationException extends Exception {

	private static final long serialVersionUID = -1304215497635960785L;

	public ValidationException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public ValidationException(String message) {
		super(message);
	}
}