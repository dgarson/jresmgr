package com.mprew.ec2.resources;

import java.io.Serializable;

import com.mprew.ec2.commons.util.ExceptionUtils;

/**
 * Status of the resource which can be both polled and reported to the
 * {@link ResourceManager}.
 * 
 * @author graywatson
 */
public class ResourceHealth implements Serializable {

	private static final long serialVersionUID = 2369438729201727363L;

	/** standard description to use if the level is {@link Level#OK} */
	public final static String OK_DESCRIPTION = "okay";
	
	/** okay health default for convenience */
	public final static ResourceHealth okHealth = new ResourceHealth(ResourceHealth.Level.OK, OK_DESCRIPTION);
	
	/**
	 * Enumerated status value.
	 */
	public enum Level {
		OK(1),
		WARNING(2),
		ERROR(3);
		
		private final int value;
		
		private Level(int value) {
			this.value = value;
		}
		
		/**
		 * @return If this level is worse that the other level.
		 */
		public boolean isWorse(Level other) {
			return this.value > other.value;
		}
	}

	private final Level level;
	private final String description;
	private final Throwable throwable;

	public ResourceHealth(Level level, String description) {
		this.level = level;
		this.description = description;
		this.throwable = null;
	}

	public ResourceHealth(Level level, String description, Throwable throwable) {
		this.level = level;
		this.description = description;
		this.throwable = throwable;
	}
	
	public Level getLevel() {
		return level;
	}

	public String getDescription() {
		return description;
	}

	public Throwable getThrowable() {
		return throwable;
	}

	/**
	 * @return If this health is ok.
	 */
	public boolean isOk() {
		return level == Level.OK;
	}

	/**
	 * Return true this health is worse that the argument health passed in.
	 */
	public boolean isWorse(ResourceHealth other) {
		return this.level.isWorse(other.level);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || (! (obj instanceof ResourceHealth))) {
			return false;
		}
		ResourceHealth other = (ResourceHealth)obj;
		if (this.level == other.level) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return level.value;
	}

	@Override
	public String toString() {
		if (throwable == null) {
			return "ResourceHealth " + level + ":" + description;
		} else {
			return "ResourceHealth " + level + ":" + description
					+ ", threw "
					+ ExceptionUtils.getRootCause(throwable);
		}
	}
}
