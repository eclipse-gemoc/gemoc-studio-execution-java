package org.eclipse.gemoc.execution.sequential.javaengine.headless.commands;

import java.io.Serializable;

import org.eclipse.gemoc.executionframework.mep.events.StoppedReason;

public class StopEvent implements Serializable {
	
	public StoppedReason stopReason;

}
