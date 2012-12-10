package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.ContextResource;
import com.mprew.ec2.resources.annotation.Pause;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Resume;
import com.mprew.ec2.resources.annotation.Stop;

@ResourceType(name = "managedResPause")
public class ManagedPausable extends ManagedA {
	
	@ContextResource(name = "awsCredentials")
	private AWSCredObj awsCreds;
	
	@Stop
	@SuppressWarnings("unused")
	private void doStop() {
		System.out.println("AWS Credentials: " + awsCreds);
		// throw new IllegalStateException("blah!");
		// do nothing
	}
	
	@Pause
	public void pauseThisShit() {
		System.out.println("Pausing the pausable");
	}
	
	@Resume
	public void resumeThisShit() {
		System.out.println("Resuming the pausable");
	}
	
	@Override
	public String toString() {
		return "managedPausable";
	}
}