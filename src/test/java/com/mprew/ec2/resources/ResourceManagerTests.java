package com.mprew.ec2.resources;

import java.util.concurrent.Future;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.mprew.ec2.commons.server.ManagedServer;

public class ResourceManagerTests {
	
	private ClassPathXmlApplicationContext appCtx;

	@Before
	public void prepareContext() {
		appCtx = new ClassPathXmlApplicationContext("classpath:com/mprew/ec2/services/server-test.xml", "classpath:com/mprew/ec2/services/run-test.xml");
		appCtx.start();
	}
	
	@Test
	public void testResourceManager() throws Exception {
		final ResourceManager resmgr = appCtx.getBean(ResourceManager.class);
		
		Thread waitForServerThread = new Thread() {
			@Override
			public void run() {
				
				try {
					Future<ManagedServer> future = resmgr.futureForResource("managedServer", ResourceState.STARTED);
					ManagedServer server = future.get();
					System.out.println("Got server instance!");
					server.waitForServer();
					System.out.println("Server stopped!");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		waitForServerThread.start();
		ManagedServer server = appCtx.getBean(ManagedServer.class);
		resmgr.registerResource("managedServer", "managedServer", server, "start", "stop", true, true);
		
		resmgr.start("managedServer");
		Assert.assertEquals(ResourceState.STARTED, resmgr.getResource("managedServer").getState());
		resmgr.start();
		Assert.assertEquals(ResourceState.STARTED, resmgr.getResource("managedServer").getState());
		resmgr.publish();
		Assert.assertEquals(ResourceState.RUNNING, resmgr.getResource("managedServer").getState());
		
		
		resmgr.stopAsync("managedResC");
		// resmgr.unregisterResource("managedResC");
	}
	
	@After
	public void destroyContext() {
		if (appCtx != null) {
			appCtx.destroy();
		}
	}
}
