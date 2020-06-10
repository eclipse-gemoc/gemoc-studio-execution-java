package org.eclipse.gemoc.execution.sequential.javaengine.headless;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.eclipse.emf.ecore.resource.Resource;
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
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ToggleBreakpointCommand;
import org.eclipse.gemoc.executionframework.engine.commons.EngineContextException;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.mep.engine.IMEPEngine;
import org.eclipse.gemoc.executionframework.mep.engine.IMEPEventListener;
import org.eclipse.gemoc.executionframework.mep.events.StoppedReason;
import org.eclipse.gemoc.executionframework.mep.launch.MEPLauncherParameters;
import org.eclipse.gemoc.executionframework.mep.types.SourceBreakpoint;
import org.eclipse.gemoc.executionframework.mep.types.Variable;
import org.eclipse.gemoc.xdsmlframework.api.core.ExecutionMode;
import org.eclipse.gemoc.xdsmlframework.api.extensions.languages.LanguageDefinitionExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeadlessPlainK3ExecutionEngineMEP<L extends LanguageDefinitionExtension> extends HeadlessPlainK3ExecutionEngine<L> implements IMEPEngine {	
	private static final Logger LOGGER = LoggerFactory.getLogger("HeadlessPlainK3ExecutionEngineMEP");
	
	Socket gemocServer = null;
	ObjectInputStream gemocServerOutput = null;
	ObjectOutputStream gemocServerInput = null;
	List<Object> serverOutputBuffer = null;
	Semaphore serverOutputBufferSem = null;
	L languageDefinition = null;
	ISequentialRunConfiguration runConfiguration = null;
	
	public HeadlessPlainK3ExecutionEngineMEP(L languageDefinition) {
		this.languageDefinition = languageDefinition;
	}
	
	@Override
	public void internalLaunchEngine(MEPLauncherParameters launchParameters) {
		Resource resourceModel = launchParameters.resourceModel;
		String modelEntryPoint = launchParameters.modelEntryPoint;
		String methodEntryPoint = launchParameters.methodEntryPoint;
		String initializationMethod = launchParameters.initializationMethod;
		String initializationMethodArgs = launchParameters.initializationMethodArgs;
		
		try {
			LOGGER.info("START launchGemocEngine()");
									
			runConfiguration = new HeadlessJavaEngineSequentialRunConfiguration(resourceModel.getURI(), languageDefinition.getName(),
					modelEntryPoint, methodEntryPoint, initializationMethod, initializationMethodArgs);
			
			AbstractHeadlessExecutionContext<ISequentialRunConfiguration, L> executioncontext = newExecutionContext(resourceModel);
			
			//HeadlessDebuggerAddon debuggerAddon =  new HeadlessDebuggerAddon();
			
			
			//executioncontext.getExecutionPlatform().addEngineAddon(debuggerAddon);
			executioncontext.initializeResourceModel();
			this.initialize(executioncontext);
			
			this.start();
			
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
			
			final IMEPEngine currentEngine = this;
			
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
								for (IMEPEventListener eventListener : mepEventListeners) {
									eventListener.outputReceived(new org.eclipse.gemoc.executionframework.mep.events.Output(currentEngine, ((OutputEvent) readData).output));
								}
							} else if (readData instanceof StopEvent) {
								for (IMEPEventListener eventListener : mepEventListeners) {
									eventListener.stopReceived(new org.eclipse.gemoc.executionframework.mep.events.Stopped(currentEngine, ((StopEvent) readData).stopReason));
								}
							} else {
								serverOutputBuffer.add(readData);
								serverOutputBufferSem.release();
							}
						} catch (EOFException e) {
							readData = null;
						} catch (ClassNotFoundException | IOException e) {
							e.printStackTrace();
							break;
						}
					} while (readData != null);
				}
			}).start();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}
	
	protected AbstractHeadlessExecutionContext<ISequentialRunConfiguration, L> newExecutionContext(Resource resourceModel) throws EngineContextException {
		return new AbstractHeadlessExecutionContext<ISequentialRunConfiguration, L>(
				runConfiguration, 
				ExecutionMode.Run, 
				languageDefinition, 
				new HeadlessExecutionWorkspace(), 
				new HeadlessExecutionPlatform()){				
					@Override
					public void initializeResourceModel() {
						_resourceModel = resourceModel;
					}
			};
	}
	
	@Override
	public StoppedReason internalNext() {
		return internalDoStep(StepKind.NEXT);
	}

	@Override
	public StoppedReason internalStepIn() {
		return internalDoStep(StepKind.STEP_IN);
	}

	@Override
	public StoppedReason internalStepOut() {
		return internalDoStep(StepKind.STEP_OUT);
	}
	
	private StoppedReason internalDoStep(StepKind stepKind) {
		DoStepCommand command = new DoStepCommand();
		command.stepKind = stepKind;
		try {
			gemocServerInput.writeObject(command);
			serverOutputBufferSem.acquire();
			Object output = serverOutputBuffer.remove(0);
			return (((StopCondition) output).stopReason);
		} catch (IOException | InterruptedException  e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public void internalSetBreakpoints(SourceBreakpoint[] breakpoints) {
		ClearBreakpointsCommand clearCommand = new ClearBreakpointsCommand();
		ToggleBreakpointCommand toggleCommand;
		try {
			gemocServerInput.writeObject(clearCommand);
			for (SourceBreakpoint bp : breakpoints) {
				toggleCommand = new ToggleBreakpointCommand();
				toggleCommand.line = (int) bp.getLine();
				gemocServerInput.writeObject(toggleCommand);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void internalTerminate() {
		this.stop();
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
	public void internalContinue() {
		ContinueCommand command = new ContinueCommand();
		try {
			gemocServerInput.writeObject(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Variable[] internalVariables() {
		ListVariablesCommand listCommand = new ListVariablesCommand();
		try {
			gemocServerInput.writeObject(listCommand);
			serverOutputBufferSem.acquire();
			List<String> variableNames = (ArrayList<String>) serverOutputBuffer.remove(0);
			Variable[] variables = new Variable[variableNames.size()];
			for (int i = 0; i < variables.length; i++) {
				String name = variableNames.get(i);
				GetVariableCommand variableCommand = new GetVariableCommand();
				variableCommand.variableQualifiedName = variableNames.get(i);
				gemocServerInput.writeObject(variableCommand);
				serverOutputBufferSem.acquire();
				String value = (String) serverOutputBuffer.remove(0);
				variables[i] = new Variable(name, value);
			}
			return variables;
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return new Variable[0];
		}
	}

	@Override
	public org.eclipse.gemoc.executionframework.mep.types.StackFrame[] internalStackTrace() {
		List<StackFrame> gemocFrames =
				this.getStackTrace();
		org.eclipse.gemoc.executionframework.mep.types.StackFrame[] mepFrames =
				new org.eclipse.gemoc.executionframework.mep.types.StackFrame[gemocFrames.size()];
		for (int i = 0; i < gemocFrames.size(); i++) {
			mepFrames[i] = new org.eclipse.gemoc.executionframework.mep.types.StackFrame((long) i,
					gemocFrames.get(i).getName(), (long) gemocFrames.get(i).getLine(), 0L);
		}
		return mepFrames;
	}

	@Override
	public String internalSource() {
		return this.getSourceContent();
	}

	private List<IMEPEventListener> mepEventListeners = new ArrayList<>();
	
	@Override
	public void addMEPEventListener(IMEPEventListener listener) {
		this.mepEventListeners.add(listener);
	}

	@Override
	public void removeMEPEventListener(IMEPEventListener listener) {
		this.mepEventListeners.remove(listener);
	}
	
	@Override
	public void removeAllMEPEventListeners() {
		this.mepEventListeners.clear();
	}
}
