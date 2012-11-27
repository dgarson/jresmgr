package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.Kill;
import com.mprew.ec2.resources.annotation.Pause;
import com.mprew.ec2.resources.annotation.Publish;
import com.mprew.ec2.resources.annotation.Restart;
import com.mprew.ec2.resources.annotation.Resume;
import com.mprew.ec2.resources.annotation.Start;
import com.mprew.ec2.resources.annotation.Stop;
import com.mprew.ec2.resources.context.ResourceContext;
import com.mprew.ec2.resources.context.ResourceContextAware;

/**
 * Interface supported by the the {@link ResourceManager} for managing the running of various resources in the backend
 * service.
 * 
 * @author graywatson
 */
public interface ManagedResourceType extends ResourceContextAware {
	
	/**
	 * Start the resource. This is after initialization and before publishing.
	 * @param context the resource context
	 * @return true if it is {@link ResourceState#STARTED} when it returns otherwise false and the resource will call
	 *         {@link ResourceManager#updateResourceState} in the future.
	 */
	@Start
	public void start(ResourceContext context) throws ResourceException;
	
	/**
	 * Pause the resource. The resource can then be resume()d again as opposed to either of the shutdown methods.
	 * @param context the resource context
	 * @return true if it is {@link ResourceState#PAUSED} when it returns otherwise false and the resource will call
	 *         {@link ResourceManager#updateResourceState} in the future.
	 */
	@Pause
	public void pause(ResourceContext context) throws ResourceException;
	
	/**
	 * Resume the resource which has previously been pause()d.
	 * @param context the resource context
	 * @return true if it is {@link ResourceState#RUNNING} when it returns otherwise false and the resource will call
	 *         {@link ResourceManager#updateResourceState} in the future.
	 */
	@Resume
	public void resume(ResourceContext context) throws ResourceException;
	
	/**
	 * Shutdown the resource in a forceful manner as opposed to {@link #shutdownGracefully}.
	 * @param context the resource context
	 * @return true if it is {@link ResourceState#SHUTDOWN_FORCEFULLY} when it returns otherwise false and the resource
	 *         will call {@link ResourceManager#updateResourceState} in the future.
	 */
	@Restart
	public void restart(ResourceContext context) throws ResourceException;
	
	/**
	 * Publish the resource where appropriate. This is after starting and before running.
	 * @param context the resource context
	 * @return true if it is {@link ResourceState#RUNNING} when it returns otherwise false and the resource will call
	 *         {@link ResourceManager#updateResourceState} in the future.
	 */
	@Publish
	public void publish(ResourceContext context) throws ResourceException;
	
	/**
	 * Shutdown the resource in a graceful manner as opposed to {@link #shutdownForcefully}.
	 * @param context the resource context
	 * @return true if it is {@link ResourceState#SHUTDOWN_GRACEFULLY} when it returns otherwise false and the resource
	 *         will call {@link ResourceManager#updateResourceState} in the future.
	 */
	@Stop
	public boolean shutdownGracefully(ResourceContext context) throws ResourceException;
	
	/**
	 * Shutdown the resource in a forceful manner as opposed to {@link #shutdownGracefully}.
	 * @param context the resource context
	 * @return true if it is {@link ResourceState#SHUTDOWN_FORCEFULLY} when it returns otherwise false and the resource
	 *         will call {@link ResourceManager#updateResourceState} in the future.
	 */
	@Kill
	public boolean shutdownForcefully(ResourceContext context) throws ResourceException;
	
	/**
	 * Injection point for the ResourceContext.
	 */
	@Override
	public void setResourceContext(ResourceContext context);
}
