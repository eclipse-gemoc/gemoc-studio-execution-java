package org.eclipse.gemoc.execution.sequential.javaengine.mep;

import java.util.concurrent.Semaphore;

import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEvent;
import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEventProcessor;
import org.eclipse.gemoc.dsl.debug.ide.event.debugger.BreakpointReply;
import org.eclipse.gemoc.dsl.debug.ide.event.debugger.SetCurrentInstructionReply;
import org.eclipse.gemoc.dsl.debug.ide.event.debugger.SpawnRunningThreadReply;
import org.eclipse.gemoc.dsl.debug.ide.event.debugger.TerminatedReply;

public class HeadlessDebugEventHandler implements IDSLDebugEventProcessor {

	private volatile Semaphore breakReached;
	
	public HeadlessDebugEventHandler() {
		this.breakReached = new Semaphore(0);
	}
	
	@Override
	public Object handleEvent(IDSLDebugEvent event) {
		if (event instanceof BreakpointReply || event instanceof TerminatedReply) {
			breakReached.release();
		}
		System.err.println("  Debug event: " + event.toString());
		return null;
	}
	
	public void waitBreakReached() throws InterruptedException {
		this.breakReached.acquire();
	}
	
	public void clearPermits() {
		this.breakReached.drainPermits();
	}
	
}
