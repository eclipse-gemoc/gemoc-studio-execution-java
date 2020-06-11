package org.eclipse.gemoc.execution.sequential.javaengine.headless.commands;

import java.io.Serializable;

public class StopCondition implements Serializable {

		public StopReason  stopReason;

		public StopCondition(StopReason stopReason) {
			super();
			this.stopReason = stopReason;
		}
		
}
