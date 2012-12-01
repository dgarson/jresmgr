package com.mprew.ec2.resources;

import static com.mprew.ec2.resources.ResourceState.INITIALIZED;
import static com.mprew.ec2.resources.ResourceState.INITIALIZING;
import static com.mprew.ec2.resources.ResourceState.NONE;
import static com.mprew.ec2.resources.ResourceState.PAUSED;
import static com.mprew.ec2.resources.ResourceState.PAUSING;
import static com.mprew.ec2.resources.ResourceState.PUBLISHING;
import static com.mprew.ec2.resources.ResourceState.RESUMING;
import static com.mprew.ec2.resources.ResourceState.RUNNING;
import static com.mprew.ec2.resources.ResourceState.SHUTDOWN_FORCEFULLY;
import static com.mprew.ec2.resources.ResourceState.SHUTDOWN_GRACEFULLY;
import static com.mprew.ec2.resources.ResourceState.SHUTTING_DOWN_FORCEFULLY;
import static com.mprew.ec2.resources.ResourceState.SHUTTING_DOWN_GRACEFULLY;
import static com.mprew.ec2.resources.ResourceState.STARTED;
import static com.mprew.ec2.resources.ResourceState.STARTING;

import java.util.concurrent.Future;

import junit.framework.Assert;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.mprew.ec2.commons.server.ManagedServer;
import com.mprew.ec2.resources.action.ResourceAction;

public class ResourceManagerTests {
	
	private static ClassPathXmlApplicationContext appCtx;

	@BeforeClass
	public static void prepareContext() {
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
		
		Thread waitForServerThread2 = new Thread() {
			@Override
			public void run() {
				
				try {
					ResourceManager.tlManager.set(resmgr);
					ResourceInfo res = resmgr.getResource(ManagedServer.class);
					System.out.println("Got server instance from thread 2");
					res.waitForState(ResourceState.STARTED);
					System.out.println("Server started in thread 2");
					res.waitForState(ResourceState.SHUTDOWN_GRACEFULLY);
					System.out.println("Server stopped in thread 2");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		waitForServerThread2.start();
		
		ManagedC obj = ObjectFactory.newInstance("resCD", ManagedC.class);
		Assert.assertNotNull(obj);
		Assert.assertNotNull(resmgr.getResource("resCD"));
		
		ManagedResShit obj2 = ObjectFactory.newInstance("resShit", ManagedResShit.class);
		Assert.assertNotNull(obj2);
		Assert.assertNotNull(resmgr.getResource("resShit"));
		
		resmgr.start("managedServer");
		Assert.assertEquals(ResourceState.STARTED, resmgr.getResource("managedServer").getState());
		resmgr.start();
		Assert.assertEquals(ResourceState.STARTED, resmgr.getResource("managedServer").getState());
		resmgr.publish();
		Assert.assertEquals(ResourceState.RUNNING, resmgr.getResource("managedServer").getState());
		
		resmgr.resume("managedResPause");
		resmgr.pause("managedResPause");
	
		// resmgr.unregisterResource("managedResC");
		
		Thread.sleep(1000);
	}
	
	@Test(expected = ImpossibleActionException.class)
	public void testPauseImpossible() throws Exception {
		final ResourceManager resmgr = appCtx.getBean(ResourceManager.class);
		// resmgr.start();
		// resmgr.publish();
		resmgr.pause("managedResA");
	}
	
	@Test(expected = ImpossibleActionException.class)
	public void testPauseFailure() throws Exception {
		final ResourceManager resmgr = appCtx.getBean(ResourceManager.class);
		resmgr.pause("managedResA");
	}
	
	@AfterClass
	public static void destroyContext() {
		if (appCtx != null) {
			appCtx.destroy();
		}
	}
	
	@Test
	public void testMetadata() throws Exception {
		ResourceMetadata metadata = new ResourceMetadata("resC", "resC", new ManagedC());
		Assert.assertEquals(ManagedC.class.getDeclaredMethod("doStop"), metadata.getResourceMethod(ResourceAction.STOPPING));
		Assert.assertEquals(ManagedA.class.getDeclaredMethod("startA"), metadata.getResourceMethod(ResourceAction.STARTING));		
		System.out.println(metadata);
		
		metadata = new ResourceMetadata("resPvt", "resPvt", new ManagedResPvt());
		Assert.assertEquals(ManagedResPvt.class.getDeclaredMethod("doStop"), metadata.getResourceMethod(ResourceAction.STOPPING));
	}
	
	@Test
	public void testNewStates() {
		Assert.assertTrue(SHUTTING_DOWN_FORCEFULLY.isNewStateOk(SHUTDOWN_FORCEFULLY));
		Assert.assertFalse(SHUTTING_DOWN_FORCEFULLY.isNewStateOk(SHUTDOWN_GRACEFULLY));
		Assert.assertEquals(SHUTDOWN_GRACEFULLY, SHUTTING_DOWN_GRACEFULLY.getStabilizeState());
		Assert.assertTrue(RESUMING.isNewStateOk(RUNNING));
		Assert.assertFalse(RESUMING.isNewStateOk(SHUTTING_DOWN_FORCEFULLY));
		Assert.assertEquals(PAUSED, PAUSING.getStabilizeState());
		Assert.assertEquals(RUNNING, PUBLISHING.getStabilizeState());
		Assert.assertTrue(STARTING.isNewStateOk(STARTED));
		Assert.assertFalse(STARTING.isNewStateOk(RUNNING));
		Assert.assertFalse(INITIALIZING.isStableState());
		Assert.assertEquals(SHUTDOWN_GRACEFULLY, SHUTDOWN_GRACEFULLY.getStabilizeState());
		Assert.assertTrue(NONE.isNewStateOk(INITIALIZING));
		Assert.assertFalse(NONE.isStableState());
		Assert.assertEquals(INITIALIZED, NONE.getStabilizeState());
	}
}
