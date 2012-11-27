package com.mprew.ec2.resources;

import org.junit.Assert;

import com.mprew.ec2.resources.annotation.ContextResource;
import com.mprew.ec2.resources.annotation.Dependencies;
import com.mprew.ec2.resources.annotation.Initialize;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Start;
import com.mprew.ec2.resources.annotation.Stop;
import com.mprew.ec2.resources.context.ResourceContext;

@ResourceType(name = "managedResB")
@Dependencies({"managedResA"})
public class ManagedB {

	@ContextResource
	private ManagedResourceObj resourceObj;
	
	@Initialize
	public void initB(ResourceInfo resource, ResourceContext ctx) {
		Assert.assertNotNull(ctx);
		System.out.println("Initializing B [" + resource.getBeanName() + "]");
	}
	
	@Start
	public void startB() {
		System.out.println("resourceObj = " + resourceObj);
	}
	
	@Stop
	public void stop() {
	}
	
	@Override
	public String toString() {
		return "managedB";
	}
}