package org.eclipse.gemoc.execution.sequential.javaengine.headless;

import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;
import org.eclipse.gemoc.xdsmlframework.api.engine_addon.IEngineAddon;

public class HeadlessDebuggerAddon implements IEngineAddon {

	@Override
	public void engineStopped(IExecutionEngine<?> engine) {
		// TODO Auto-generated method stub
		IEngineAddon.super.engineStopped(engine);
	}

	@Override
	public void aboutToExecuteStep(IExecutionEngine<?> engine, Step<?> stepToExecute) {
		// TODO Auto-generated method stub
		IEngineAddon.super.aboutToExecuteStep(engine, stepToExecute);
		
		/*
		 val ToPushPop stackModification = new ToPushPop(step, true);
		toPushPop.add(stackModification);
		val boolean shallcontinue = control(threadName, step);
		if (!shallcontinue) {
			throw new EngineStoppedException("Debug thread has stopped.");
		}
		 */
	}

	@Override
	public void stepExecuted(IExecutionEngine<?> engine, Step<?> stepExecuted) {
		// TODO Auto-generated method stub
		IEngineAddon.super.stepExecuted(engine, stepExecuted);
	}

	
	
}
