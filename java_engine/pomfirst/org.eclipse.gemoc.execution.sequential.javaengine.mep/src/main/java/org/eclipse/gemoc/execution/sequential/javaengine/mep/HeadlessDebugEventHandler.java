package org.eclipse.gemoc.execution.sequential.javaengine.mep;

import java.util.concurrent.Semaphore;

import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEvent;
import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEventProcessor;
import org.eclipse.gemoc.dsl.debug.ide.event.debugger.BreakpointReply;
import org.eclipse.gemoc.dsl.debug.ide.event.debugger.SpawnRunningThreadReply;
import org.eclipse.gemoc.dsl.debug.ide.event.debugger.TerminatedReply;

public class HeadlessDebugEventHandler implements IDSLDebugEventProcessor {

	private volatile Semaphore breakReached;
	private volatile boolean simulationEnded;
	
	@Override
	public Object handleEvent(IDSLDebugEvent event) {
		if (event instanceof SpawnRunningThreadReply) {
			simulationEnded = false;
			breakReached = new Semaphore(-1);
		} else if (event instanceof BreakpointReply) {
			breakReached.release();
		} else if (event instanceof TerminatedReply) {
			simulationEnded = true;
			breakReached.release();
		}
		//System.out.println("  Debug event: " + event.toString());
		return null;
	}
	
	public boolean isSimulationEnded() {
		return this.simulationEnded;
	}
	
	public void waitBreakReached() throws InterruptedException {
		this.breakReached.acquire();
	}
	
}
