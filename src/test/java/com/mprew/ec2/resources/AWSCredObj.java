package com.mprew.ec2.resources;

public class AWSCredObj implements ManagedObject {
	
	private String accessKey;
	private String secretKey;
	
	@Override
	public String getResourceName() {
		return "awsCredentials";
	}
	
	public String getSecretKey() {
		return secretKey;
	}
	
	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}
	
	public String getAccessKey() {
		return accessKey;
	}
	
	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}
}