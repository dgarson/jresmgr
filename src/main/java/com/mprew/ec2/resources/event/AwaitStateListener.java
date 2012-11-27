package com.mprew.ec2.resources.event;

import java.util.concurrent.CountDownLatch;

import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.event.ResourceEvent.EventType;

/**
 * A ResourceListener that contains a CountDownLatch initialized with a counter of one. Whenever a single matching ResourceEvent 
 * is received, or any ResourceFailedEvent is received, the countdown latch is decremented and allows the method <code>awaitState()</code>
 * to execute.
 * 
 * @author dgarson
 */
public abstract class AwaitStateListener implements FilteredResourceListener {
	
	private ResourceInfo resource;
	private EventType awaitEventType;
	private volatile boolean success = false;
	private final CountDownLatch latch = new CountDownLatch(1);
	
	public AwaitStateListener(ResourceInfo resource, EventType eventType) {
		this.resource = resource;
		this.awaitEventType = eventType;
	}
	
	@Override
	public boolean accepts(ResourceInfo info) {
		return info.getResourceName().equals(resource.getResourceName());
	}
	
	@Override
	public void onResourceEvent(ResourceEvent event) {
		if (event.getType() == awaitEventType) {
			success = true;
			latch.countDown();
			done();
		}
	}
	
	@Override
	public void onResourceFailure(ResourceFailedEvent event) {
		success = false;
		latch.countDown();
		done();
	}
	
	protected abstract void done();
	
	/**
	 * Awaits the specified event and then returns success or failure.
	 * @return true on success (event received), false on failure event received instead
	 * @throws InterruptedException if interrupted while waiting
	 */
	public boolean awaitState() throws InterruptedException {
		latch.await();
		return success;
	}
}