package org.eclipse.gemoc.execution.sequential.javaengine.headless.mep;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.AbstractHeadlessExecutionContext;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.HeadlessJavaEngineSequentialRunConfiguration;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.HeadlessPlainK3ExecutionEngine;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ClearBreakpointsCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ContinueCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.DoStepCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.GetVariableCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ListVariablesCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.OutputEvent;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StepKind;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopCondition;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopEvent;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopReason;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ToggleBreakpointCommand;
import org.eclipse.gemoc.executionframework.engine.commons.EngineContextException;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.mep.launch.GemocMEPServerImpl;
import org.eclipse.gemoc.xdsmlframework.api.extensions.languages.LanguageDefinitionExtension;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class K3GemocMEPServerImpl<T extends LanguageDefinitionExtension> extends GemocMEPServerImpl {

	private static final Logger LOGGER = LoggerFactory.getLogger(K3GemocMEPServerImpl.class);
	
	protected T languageDefinition;
	protected ISequentialRunConfiguration runConfiguration;
	
	Socket gemocServer = null;
	ObjectInputStream gemocServerOutput = null;
	ObjectOutputStream gemocServerInput = null;
	List<Object> serverOutputBuffer = null;
	Semaphore serverOutputBufferSem = null;
	

	HeadlessPlainK3ExecutionEngine currentExecutionEngine;
	
	@Override
	public void launchGemocEngine(Resource resourceModel, String modelEntryPoint, String methodEntryPoint, String initializationMethod, String initializationMethodArgs) {
		try {
			LOGGER.info("START launchGemocEngine()");
			HeadlessPlainK3ExecutionEngine executionEngine = new HeadlessPlainK3ExecutionEngine(); 
									
			runConfiguration = new HeadlessJavaEngineSequentialRunConfiguration(resourceModel.getURI(), languageDefinition.getName(),
					modelEntryPoint, methodEntryPoint, initializationMethod, initializationMethodArgs);
			
			AbstractHeadlessExecutionContext<ISequentialRunConfiguration, T> executioncontext = newExecutionContext(resourceModel);
			
			//HeadlessDebuggerAddon debuggerAddon =  new HeadlessDebuggerAddon();
			
			
			//executioncontext.getExecutionPlatform().addEngineAddon(debuggerAddon);
			executioncontext.initializeResourceModel();
			executionEngine.initialize(executioncontext);
			
			executionEngine.start();
			currentExecutionEngine = executionEngine;
			
			boolean connected = false;
			// Retry until server is available
			while (!connected) {
				try {
					gemocServer = new Socket("localhost", HeadlessPlainK3ExecutionEngine.GEMOC_PORT);
					connected = true;
				} catch (ConnectException e) {
					System.out.println("Could not connect to GEMOC server, retrying...");
					Thread.sleep(1000);
				}
			}
			gemocServerOutput = new ObjectInputStream(gemocServer.getInputStream());
			gemocServerInput = new ObjectOutputStream(gemocServer.getOutputStream());
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					Object readData;
					serverOutputBuffer = new ArrayList<Object>();
					serverOutputBufferSem = new Semaphore(0);
					do {
						try {
							readData = gemocServerOutput.readObject();
							if (readData instanceof OutputEvent) {
								sendOutput(((OutputEvent) readData).output);
							} else if (readData instanceof StopEvent) {
								manageStop(((StopEvent) readData).stopReason);
							} else {
								serverOutputBuffer.add(readData);
								serverOutputBufferSem.release();
							}
						} catch (ClassNotFoundException | IOException e) {
							e.printStackTrace();
							break;
						}
					} while (readData != null);
				}
			}).start();
			//executionEngine.thread.join();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	abstract protected AbstractHeadlessExecutionContext<ISequentialRunConfiguration, T> newExecutionContext(Resource resourceModel) throws EngineContextException;
	
	@Override
	protected void internalNext() {
		internalDoStep(StepKind.NEXT);
	}
	
	@Override
	protected void internalStepIn() {
		internalDoStep(StepKind.STEP_IN);
	}
	
	@Override
	protected void internalStepOut() {
		internalDoStep(StepKind.STEP_OUT);
	}
	
	private void internalDoStep(StepKind stepKind) {
		DoStepCommand command = new DoStepCommand();
		command.stepKind = stepKind;
		try {
			gemocServerInput.writeObject(command);
			serverOutputBufferSem.acquire();
			Object output = serverOutputBuffer.remove(0);
			manageStop(((StopCondition) output).stopReason);
		} catch (IOException | InterruptedException  e) {
			e.printStackTrace();
		}
	}
	
	protected void manageStop(StopReason stopReason) {
		switch (stopReason) {
			case REACHED_BREAKPOINT:
				StoppedEventArguments stoppedArgsBreakpoint = new StoppedEventArguments();
				stoppedArgsBreakpoint.setReason("breakpoint");
				stoppedArgsBreakpoint.setDescription("Reached breakpoint");
				client.stopped(stoppedArgsBreakpoint);
				break;
			case REACHED_NEXT_LOGICAL_STEP:
				StoppedEventArguments stoppedArgsStep = new StoppedEventArguments();
				stoppedArgsStep.setReason("step");
				stoppedArgsStep.setDescription("Reached new logical step");
				client.stopped(stoppedArgsStep);
				break;
			case REACHED_SIMULATION_END:
				TerminatedEventArguments terminatedArgs = new TerminatedEventArguments();
				client.terminated(terminatedArgs);
				simulationStarted = false;
				break;
			case TIME:
				break;
			default:
				break;
		}
	}

	@Override
	protected void internalTerminate() {
		currentExecutionEngine.stop();
		StopCommand stopCommand = new StopCommand();
		try {
			gemocServerInput.writeObject(stopCommand);
			serverOutputBufferSem.acquire();
			serverOutputBuffer.remove(0);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected Variable[] internalVariables() {
		ListVariablesCommand listCommand = new ListVariablesCommand();
		try {
			gemocServerInput.writeObject(listCommand);
			serverOutputBufferSem.acquire();
			List<String> variableNames = (ArrayList<String>) serverOutputBuffer.remove(0);
			Variable[] variables = new Variable[variableNames.size()];
			for (int i = 0; i < variables.length; i++) {
				Variable variable = new Variable();
				variable.setName(variableNames.get(i));
				GetVariableCommand variableCommand = new GetVariableCommand();
				variableCommand.variableQualifiedName = variableNames.get(i);
				gemocServerInput.writeObject(variableCommand);
				serverOutputBufferSem.acquire();
				variable.setValue((String) serverOutputBuffer.remove(0));
				variables[i] = variable;
			}
			return variables;
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return new Variable[0];
		}
	}

	@Override
	protected StackFrame[] internalStackTrace() {
		List<org.eclipse.gemoc.execution.sequential.javaengine.headless.StackFrame> gemocFrames =
				currentExecutionEngine.getStackTrace();
		StackFrame[] mepFrames = new StackFrame[gemocFrames.size()];
		for (int i = 0; i < gemocFrames.size(); i++) {
			StackFrame mepFrame = new StackFrame();
			mepFrame.setId((long) i);
			mepFrame.setName(gemocFrames.get(i).getName());
			mepFrame.setLine((long) gemocFrames.get(i).getLine());
			mepFrame.setColumn(0L);
			mepFrames[i] = mepFrame;
		}
		return mepFrames;
	}
	
	

	@Override
	protected String internalSource() {
		return currentExecutionEngine.getSourceContent();
	}

	@Override
	protected void internalClearBreakpoints() {
		ClearBreakpointsCommand command = new ClearBreakpointsCommand();
		try {
			gemocServerInput.writeObject(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void internalToggleBreakpoint(int line) {
		ToggleBreakpointCommand command = new ToggleBreakpointCommand();
		command.line = line;
		try {
			gemocServerInput.writeObject(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void internalContinue() {
		ContinueCommand command = new ContinueCommand();
		try {
			gemocServerInput.writeObject(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
