package com.mprew.ec2.resources;

import com.mprew.ec2.commons.server.ManagedServer;
import com.mprew.ec2.resources.annotation.ContextProperty;
import com.mprew.ec2.resources.annotation.Publish;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Start;
import com.mprew.ec2.resources.annotation.Stop;
import com.mprew.ec2.resources.context.ResourceContext;

@ResourceType(name = "managedResourceObj")
public class ManagedResourceObj {
	
	@ContextProperty(name = "managedServer", state = ResourceState.STARTED)
	private ManagedServer server;
	
	public ManagedResourceObj() {
	}
	
	@Publish
	public void publish(ResourceContext ctx) {
		System.out.println("Server is non-null? " + (server != null));
	}
	
	@Start
	public void start(ResourceContext ctx) {
		System.out.println("Starting ManagedResourceObj");
		//throw new RuntimeException("Wtf i crashed!");
	}
	
	@Stop
	public void stop(ResourceContext ctx) {
		System.out.println("Stopping ManagedResourceObj");
	}
}