package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.ContextResource;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Start;
import com.mprew.ec2.resources.annotation.Stop;

@ResourceType(name = "managedResA")
public class ManagedA {

	@ContextResource
	private ManagedResourceObj resourceObj;
	
	@ContextResource
	private ManagedB resB;
	
	@ContextResource(name = "managedResC")
	private ManagedA resC;
	
	@Start
	public void startA() {
		System.out.println("resourceObj = " + resourceObj);
		System.out.println("resB = " + resB);
		System.out.println("resC = " + resC);
	}
	
	@Stop
	public void stop() {
	}
	
	@Override
	public String toString() {
		return "managedA";
	}
}