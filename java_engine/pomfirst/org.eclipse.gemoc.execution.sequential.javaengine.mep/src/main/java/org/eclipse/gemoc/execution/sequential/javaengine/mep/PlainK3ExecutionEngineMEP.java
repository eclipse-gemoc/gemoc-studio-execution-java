package org.eclipse.gemoc.execution.sequential.javaengine.mep;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.gemoc.commons.utils.ModelAwarePrintStream;
import org.eclipse.gemoc.dsl.debug.ide.event.model.AbstractStepRequest;
import org.eclipse.gemoc.dsl.debug.ide.event.model.ResumeRequest;
import org.eclipse.gemoc.dsl.debug.ide.event.model.StepIntoRequest;
import org.eclipse.gemoc.dsl.debug.ide.event.model.StepOverRequest;
import org.eclipse.gemoc.dsl.debug.ide.event.model.StepReturnRequest;
import org.eclipse.gemoc.dsl.debug.ide.event.model.TerminateRequest;
import org.eclipse.gemoc.execution.sequential.javaengine.PlainK3ExecutionEngine;
import org.eclipse.gemoc.executionframework.engine.headless.AbstractSequentialHeadlessExecutionContext;
import org.eclipse.gemoc.executionframework.engine.headless.HeadlessExecutionPlatform;
import org.eclipse.gemoc.executionframework.engine.headless.HeadlessExecutionWorkspace;
import org.eclipse.gemoc.executionframework.engine.headless.HeadlessJavaEngineSequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.debugger.DefaultDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.IDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.MutableField;
import org.eclipse.gemoc.executionframework.engine.commons.EngineContextException;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.headless.FakeOSGI;
import org.eclipse.gemoc.executionframework.mep.engine.IMEPEngine;
import org.eclipse.gemoc.executionframework.mep.engine.IMEPEventListener;
import org.eclipse.gemoc.executionframework.mep.events.Output;
import org.eclipse.gemoc.executionframework.mep.events.Stopped;
import org.eclipse.gemoc.executionframework.mep.events.StoppedReason;
import org.eclipse.gemoc.executionframework.mep.launch.MEPLauncherParameters;
import org.eclipse.gemoc.executionframework.mep.types.SourceBreakpoint;
import org.eclipse.gemoc.executionframework.mep.types.StackFrame;
import org.eclipse.gemoc.executionframework.mep.types.Variable;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceEngineAddon;
import org.eclipse.gemoc.xdsmlframework.api.core.ExecutionMode;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;
import org.eclipse.gemoc.xdsmlframework.api.extensions.languages.LanguageDefinitionExtension;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;


public class PlainK3ExecutionEngineMEP<L extends LanguageDefinitionExtension> extends PlainK3ExecutionEngine implements IMEPEngine {

	ISequentialRunConfiguration runConfiguration = null;
	L languageDefinition;
	HeadlessGenericSequentialModelDebugger modelDebugger = null;
	HeadlessDebugEventHandler debugEventHandler = null;
	AbstractSequentialHeadlessExecutionContext executionContext = null;
	IDynamicPartAccessor partAccessor = null;
	
	private PrintStream baseStream;
	private ModelAwarePrintStream modelPrintStream;
		
	public PlainK3ExecutionEngineMEP(L languageDefinition) {
		this.languageDefinition = languageDefinition;
	}
	
	@Override
	protected Class<?> findEntryPointClass(String aspectClassName) {
		Class<?> entryPointClass;
		try {
			entryPointClass = Thread.currentThread().getContextClassLoader().loadClass(aspectClassName);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(
					"Could not find class " + getExecutionContext().getRunConfiguration().getExecutionEntryPoint()
							);
		}
		return entryPointClass;
	}
	
	@Override
	protected List<Object> findEntryPointMethodeParameters(EObject root) {
		List<Object> entryPointMethodParameters = new ArrayList<>();
		entryPointMethodParameters.add(root);
		return entryPointMethodParameters;
	}
	
	@Override
	public void internalLaunchEngine(MEPLauncherParameters launchParameters) {
		Resource resourceModel = launchParameters.resourceModel;
		String languageName = launchParameters.languageName;
		String modelEntryPoint = launchParameters.modelEntryPoint;
		String methodEntryPoint = launchParameters.methodEntryPoint;
		String initializationMethod = launchParameters.initializationMethod;
		String initializationMethodArgs = launchParameters.initializationMethodArgs;
		
		runConfiguration = new HeadlessJavaEngineSequentialRunConfiguration(resourceModel.getURI(), languageName,
				modelEntryPoint, methodEntryPoint, initializationMethod, initializationMethodArgs);
		
		try {
			executionContext = newExecutionContext(resourceModel);	
			executionContext.initializeResourceModel();
			
			FakeOSGI.start();
			org.eclipse.emf.transaction.TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(resourceModel.getResourceSet());
			
			this.initialize(executionContext);			
			
			debugEventHandler = new HeadlessDebugEventHandler();
			modelDebugger = new HeadlessGenericSequentialModelDebugger(debugEventHandler, this);
			executionContext.getExecutionPlatform().addEngineAddon(modelDebugger);
			partAccessor = new DefaultDynamicPartAccessor();
			
			// break on start
			modelDebugger.addPredicateBreak(new BiPredicate<IExecutionEngine<?>, Step<?>>() {
				@Override
				public boolean test(IExecutionEngine<?> t, Step<?> u) {
					return true;
				}
			});
			
			// Try to do something with trace addon
			GenericTraceEngineAddon traceAddon = new GenericTraceEngineAddon();
			executionContext.getExecutionPlatform().addEngineAddon(traceAddon);
			
			this.start();
		} catch (EngineContextException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void notifyEngineAboutToStart() {
		super.notifyEngineAboutToStart();

		final IMEPEngine engine = this;
		ByteArrayOutputStream modelOutputStream = new ByteArrayOutputStream() {
			@Override
			public synchronized void write(int b) {
				write(new byte[] {(byte)b}, 0, 1);
			}

			@Override
			public synchronized void write(byte[] b, int off, int len) {
				Output outputEvent = new Output(engine, new String(Arrays.copyOfRange(b, off, len)));
				notifyListeners(outputEvent);
			}
		};
		baseStream = System.out;
		modelPrintStream = new ModelAwarePrintStream(modelOutputStream, baseStream);
		modelPrintStream.registerModelExecutionThread(thread);
		System.setOut(modelPrintStream);
	}

	protected AbstractSequentialHeadlessExecutionContext newExecutionContext(Resource resourceModel) throws EngineContextException {
		return new AbstractSequentialHeadlessExecutionContext(
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
	public void internalNext() {
		internalDoStep(new StepOverRequest(modelDebugger.getThreadName(),
				modelDebugger.getCurrentInstruction()));
	}

	@Override
	public void internalStepIn() {
		internalDoStep(new StepIntoRequest(modelDebugger.getThreadName(),
				modelDebugger.getCurrentInstruction()));
	}

	@Override
	public void internalStepOut() {
		internalDoStep(new StepReturnRequest(modelDebugger.getThreadName(),
				modelDebugger.getCurrentInstruction()));
	}
	
	private void internalDoStep(AbstractStepRequest step) {
		modelDebugger.handleEvent(step);
		try {
			debugEventHandler.waitBreakReached();
			if (debugEventHandler.isSimulationEnded()) {
				System.setOut(baseStream);
				notifyListeners(new Stopped(this, StoppedReason.REACHED_SIMULATION_END));
			} else {
				notifyListeners(new Stopped(this, StoppedReason.REACHED_NEXT_LOGICAL_STEP));
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void internalSetBreakpoints(SourceBreakpoint[] breakpoints) {
		// TODO Clear existing breakpoints
		for (SourceBreakpoint bp : breakpoints) {
			modelDebugger.addPredicateBreakpoint(new BiPredicate<IExecutionEngine<?>, Step<?>>() {
				@Override
				public boolean test(IExecutionEngine<?> t, Step<?> u) {
					return NodeModelUtils.getNode(u.getMseoccurrence().getMse().getCaller()).getStartLine() == bp.getLine();
				}
			});
		}
	}

	@Override
	public void internalTerminate() {
		System.setOut(baseStream);
		modelDebugger.handleEvent(new TerminateRequest(modelDebugger.getThreadName()));
	}

	@Override
	public void internalContinue() {
		modelDebugger.handleEvent(new ResumeRequest(modelDebugger.getThreadName()));
	}

	@Override
	public Variable[] internalVariables() {
		EObject rootElement = executionContext.getResourceModel().getEObject(
				executionContext.getRunConfiguration().getModelEntryPoint());
		List<Variable> variables = new ArrayList<>();
		for (MutableField mf : partAccessor.extractMutableField(rootElement)) {
			variables.add(new Variable(mf.getName(), mf.getValue() ==  null ? "null" : mf.getValue().toString()));
		}
		return variables.toArray(new Variable[0]);
	}

	@Override
	public StackFrame[] internalStackTrace() {
		EObject eObj = modelDebugger.getCurrentInstruction();
		List<StackFrame> mepFrames = new ArrayList<StackFrame>();
		long i = 0L;
		while (eObj != null) {
			ICompositeNode node = NodeModelUtils.getNode(eObj);
			StackFrame frame = new StackFrame(i++, eObj.toString(), (long) node.getStartLine(), 0L);
			mepFrames.add(frame);
			eObj = eObj.eContainer();
		}
		return mepFrames.toArray(new StackFrame[0]);
	}

	@Override
	public String internalSource() {
		EObject rootElement = executionContext.getResourceModel().getEObject(
				executionContext.getRunConfiguration().getModelEntryPoint());
		return NodeModelUtils.getNode(rootElement).getText();
	}

	private List<IMEPEventListener> mepEventListeners = new ArrayList<>();
	
	@Override
	public void addMEPEventListener(IMEPEventListener listener) {
		mepEventListeners.add(listener);
	}

	@Override
	public void removeMEPEventListener(IMEPEventListener listener) {
		mepEventListeners.remove(listener);
	}

	@Override
	public void removeAllMEPEventListeners() {
		mepEventListeners.clear();
	}
	
	private void notifyListeners(Stopped event) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (IMEPEventListener eventListener : mepEventListeners) {
					eventListener.stopReceived(event);
				}
			}
		}).start();
	}
	
	private void notifyListeners(Output event) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (IMEPEventListener eventListener : mepEventListeners) {
					eventListener.outputReceived(event);
				}
			}
		}).start();
	}
	
}
