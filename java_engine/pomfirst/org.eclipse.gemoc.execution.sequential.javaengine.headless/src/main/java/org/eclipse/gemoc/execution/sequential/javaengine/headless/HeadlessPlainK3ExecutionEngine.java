package org.eclipse.gemoc.execution.sequential.javaengine.headless;



import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.RegistryFactory;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.spi.IRegistryProvider;
import org.eclipse.core.runtime.spi.RegistryStrategy;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.gemoc.commons.utils.ModelAwarePrintStream;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ClearBreakpointsCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ContinueCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.DoStepCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.GetVariableCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ListVariablesCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.OutputEvent;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StackFrame;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StepKind;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopCommand;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopCondition;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopEvent;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.StopReason;
import org.eclipse.gemoc.execution.sequential.javaengine.headless.commands.ToggleBreakpointCommand;
import org.eclipse.gemoc.executionframework.debugger.DefaultDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.IDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.MutableField;
import org.eclipse.gemoc.executionframework.engine.commons.GenericModelExecutionContext;
import org.eclipse.gemoc.executionframework.engine.commons.K3DslHelper;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.core.AbstractCommandBasedSequentialExecutionEngine;
import org.eclipse.gemoc.executionframework.engine.core.EngineStoppedException;
import org.eclipse.gemoc.executionframework.engine.headless.AbstractHeadlessExecutionContext;
import org.eclipse.gemoc.executionframework.engine.headless.FakeOSGI;
import org.eclipse.gemoc.xdsmlframework.api.extensions.languages.LanguageDefinitionExtension;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicDouble;

import fr.inria.diverse.k3.al.annotationprocessor.stepmanager.IStepManager;
import fr.inria.diverse.k3.al.annotationprocessor.stepmanager.StepCommand;
import fr.inria.diverse.k3.al.annotationprocessor.stepmanager.StepManagerRegistry;


/**
 * Implementation of the GEMOC Execution engine dedicated to run Kermeta 3
 * operational semantic
 * 
 * @author Didier Vojtisek<didier.vojtisek@inria.fr>
 *
 */
public class HeadlessPlainK3ExecutionEngine<L extends LanguageDefinitionExtension> extends AbstractCommandBasedSequentialExecutionEngine<AbstractHeadlessExecutionContext<ISequentialRunConfiguration,  L >, ISequentialRunConfiguration> implements IStepManager {
//public class HeadlessPlainK3ExecutionEngine< L extends LanguageDefinitionExtension> extends AbstractSequentialExecutionEngine<AbstractHeadlessExecutionContext<ISequentialRunConfiguration,  L >, ISequentialRunConfiguration>		implements IStepManager {


	private static final Logger LOGGER = LoggerFactory.getLogger("HeadlessPlainK3ExecutionEngine");
		
	private Method initializeMethod;
	private List<Object> initializeMethodParameters;
	private Method entryPointMethod;
	private List<Object> entryPointMethodParameters;
	private Class<?> entryPointClass;
	
	private EObject root;
	private IDynamicPartAccessor partAccessor;
	private Map<String, Object> variables;
	private Map<Integer, Boolean> breakpoints;
	
	private ByteArrayOutputStream outputStream;
	
	@Override
	public String engineKindName() {
		return "GEMOC Kermeta HEADLESS Sequential Engine";
	}

	/**
	 * Constructs a PlainK3 execution engine using an entry point (~ a main
	 * operation) The entrypoint will register itself as a StepManager into the K3
	 * step manager registry, and unregister itself at the end. As a StepManager,
	 * the PlainK3ExecutionEngine will receive callbacks through its "executeStep"
	 * operation.
	 */
	@Override
	protected void prepareEntryPoint(AbstractHeadlessExecutionContext<ISequentialRunConfiguration, L> executionContext) {
		outputStream = new ByteArrayOutputStream() {
			@Override
			public synchronized void write(int b) {
				write(new byte[] {(byte)b}, 0, 1);
			}

			@Override
			public synchronized void write(byte[] b, int off, int len) {
				OutputEvent outputEvent = new OutputEvent();
				outputEvent.output = new String(Arrays.copyOfRange(b, off, len));
				try {
					cout.writeObject(outputEvent);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		
		/*
		 * Get info from the RunConfiguration
		 */
		String entryPoint = executionContext.getRunConfiguration().getExecutionEntryPoint();
		String mainModelElementURI = executionContext.getRunConfiguration().getModelEntryPoint();

		/*
		 * Find the entry point in the workspace
		 */
		final String prefix = "public static void ";
		int startName = prefix.length();
		int endName = entryPoint.lastIndexOf("(");
		String methodFullName = entryPoint.substring(startName, endName);

		String aspectClassName = methodFullName.substring(0, methodFullName.lastIndexOf("."));
		String methodName = methodFullName.substring(methodFullName.lastIndexOf(".") + 1);

		
		// try to fake osgi start
		FakeOSGI.start();
		
		
	/*	Bundle bundle = findBundle(executionContext, aspectClassName);
		if (bundle == null)
			throw new RuntimeException("Could not find bundle for language \""
					+ executionContext.getRunConfiguration().getLanguageName() + "\"");
*/
		// search the class
		try {
			entryPointClass = Thread.currentThread().getContextClassLoader().loadClass(aspectClassName);
		} catch (ClassNotFoundException e) {
			//String bundleName = bundle.getHeaders().get("Bundle-Name");
			e.printStackTrace();
			throw new RuntimeException(
					"Could not find class " + executionContext.getRunConfiguration().getExecutionEntryPoint()
							);
		}

		// search the method
		this.entryPointMethodParameters = new ArrayList<>();
		root = executionContext.getResourceModel().getEObject(mainModelElementURI);
		entryPointMethodParameters.add(root);
		
		partAccessor = new DefaultDynamicPartAccessor();
		variables = new HashMap<>();
		breakpoints = new HashMap<>();
		
		try {
			this.entryPointMethod = K3DslHelper.findMethod(entryPointClass, root, methodName);
		} catch (Exception e) {
			String msg = "There is no \"" + methodName + "\" method in " + entryPointClass.getName()
					+ " with first parameter able to handle " + entryPointMethodParameters.get(0).toString();
			msg += " from " + ((EObject) entryPointMethodParameters.get(0)).eClass().getEPackage().getNsURI();
			LOGGER.error(msg, e);
			throw new RuntimeException("Could not find method main with correct parameters.");
		}
	}
	
	protected void prepareInitializeModel(
			AbstractHeadlessExecutionContext<ISequentialRunConfiguration, L> executionContext) {
	//protected void prepareInitializeModel(GenericModelExecutionContext<ISequentialRunConfiguration> executionContext) {

		// try to get the initializeModelRunnable
		String modelInitializationMethodQName = executionContext.getRunConfiguration().getModelInitializationMethod();
		if (!modelInitializationMethodQName.isEmpty()) {
			// the current system supposes that the modelInitialization method
			// is in the same class as the entry point
			String modelInitializationMethodName = modelInitializationMethodQName
					.substring(modelInitializationMethodQName.lastIndexOf(".") + 1);
			boolean isListArgs = false;
			boolean isEListArgs = false;
			boolean isFound = false;
			try {
				Class<?>[] modelInitializationParamType = new Class[] {
						entryPointMethodParameters.get(0).getClass().getInterfaces()[0], String[].class };
				initializeMethod = entryPointClass.getMethod(modelInitializationMethodName,
						modelInitializationParamType);
				isListArgs = false; // this is a java array
				isFound = true;
			} catch (Exception e) {

			}
			if (!isFound) {
				try {
					Class<?>[] modelInitializationParamType = new Class[] {
							entryPointMethodParameters.get(0).getClass().getInterfaces()[0], List.class };
					initializeMethod = entryPointClass.getMethod(modelInitializationMethodName,
							modelInitializationParamType);
					isListArgs = true; // this is a List
					isFound = true;
				} catch (Exception e) {

				}
			}
			if (!isFound) {
				try {
					Class<?>[] modelInitializationParamType = new Class[] {
							entryPointMethodParameters.get(0).getClass().getInterfaces()[0], EList.class };
					this.initializeMethod = entryPointClass.getMethod(modelInitializationMethodName,
							modelInitializationParamType);
					isEListArgs = true; // this is an EList
				} catch (Exception e) {
					String msg = "There is no \"" + modelInitializationMethodName + "\" method in "
							+ entryPointClass.getName() + " with first parameter able to handle "
							+ entryPointMethodParameters.get(0).toString();
					msg += " and String[] or List<String> or EList<String> args as second parameter";
					msg += " from " + ((EObject) entryPointMethodParameters.get(0)).eClass().getEPackage().getNsURI();
					LOGGER.error(msg, e);
					// ((EObject)parameters.get(0)).eClass().getEPackage().getNsURI()
					throw new RuntimeException(
							"Could not find method " + modelInitializationMethodName + " with correct parameters.");
				}
			}
			final boolean finalIsListArgs = isListArgs;
			final boolean finalIsEListArgs = isEListArgs;
			this.initializeMethodParameters = new ArrayList<>();
			initializeMethodParameters.add(entryPointMethodParameters.get(0));
			if (finalIsListArgs) {
				final ArrayList<Object> modelInitializationListParameters = new ArrayList<>();
				for (String s : executionContext.getRunConfiguration().getModelInitializationArguments()
						.split("\\r?\\n")) {
					modelInitializationListParameters.add(s);
				}
				initializeMethodParameters.add(modelInitializationListParameters);
			} else if (finalIsEListArgs) {
				final EList<Object> modelInitializationListParameters = new BasicEList<>();
				for (String s : executionContext.getRunConfiguration().getModelInitializationArguments()
						.split("\\r?\\n")) {
					modelInitializationListParameters.add(s);
				}
				initializeMethodParameters.add(modelInitializationListParameters);
			} else {
				initializeMethodParameters
						.add(executionContext.getRunConfiguration().getModelInitializationArguments().split("\\r?\\n"));
			}
		}
	}
	
	/**
	 * Invoke the initialize method
	 */
	private void callInitializeModel() {
		try {
			initializeMethod.invoke(null, initializeMethodParameters.toArray());
		} catch (EngineStoppedException stopExeception) {
			// not really an error, simply forward the stop exception
			throw stopExeception;
		} catch (java.lang.reflect.InvocationTargetException ite) {
			// not really an error, simply forward the stop exception
			if (ite.getCause() instanceof EngineStoppedException) {
				throw (EngineStoppedException) ite.getCause();
			} else {
				throw new RuntimeException(ite);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void initializeModel() {
		if (initializeMethod != null) {
			StepManagerRegistry.getInstance().registerManager(HeadlessPlainK3ExecutionEngine.this);
			try {
				final boolean isStepMethod = initializeMethod
						.isAnnotationPresent(fr.inria.diverse.k3.al.annotationprocessor.Step.class);
				startDoStepSemaphore = new Semaphore(0);
				simulationEnded = false;
				stopReceived = false;
				continueSimulation = false;
				stepKind = StepKind.STEP_IN;
				stepCaller = null;
				if (!isStepMethod) {
					finishDoStepSemaphore = new Semaphore(-1);
					fr.inria.diverse.k3.al.annotationprocessor.stepmanager.StepCommand command = new fr.inria.diverse.k3.al.annotationprocessor.stepmanager.StepCommand() {
						@Override
						public void execute() {
							callInitializeModel();
						}
					};
					fr.inria.diverse.k3.al.annotationprocessor.stepmanager.IStepManager stepManager = HeadlessPlainK3ExecutionEngine.this;
					stepManager.executeStep(entryPointMethodParameters.get(0), command, entryPointClass.getName(),
							initializeMethod.getName());
				} else {
					// FinishDoStep needs to be at -1 before the first step
					finishDoStepSemaphore = new Semaphore(-2);
					increment(startDoStepSemaphore);
					callInitializeModel();
				}
			} finally {
				StepManagerRegistry.getInstance().unregisterManager(HeadlessPlainK3ExecutionEngine.this);
			}
		}
	}

	@Override
	protected void executeEntryPoint() {
		StepManagerRegistry.getInstance().registerManager(HeadlessPlainK3ExecutionEngine.this);
		try {
			// since aspect's methods are static, first arg is null
			//entryPointMethod.invoke(null, entryPointMethodParameters.get(0));
			simulate();
		} catch (EngineStoppedException stopExeception) {
			// not really an error, simply forward the stop exception
			throw stopExeception;
		} catch (java.lang.reflect.InvocationTargetException ite) {
			// not really an error, simply forward the stop exception
			if (ite.getCause() instanceof EngineStoppedException) {
				throw (EngineStoppedException) ite.getCause();
			} else {
				throw new RuntimeException(ite);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			StepManagerRegistry.getInstance().unregisterManager(HeadlessPlainK3ExecutionEngine.this);
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e) {
					LOGGER.error(e.getMessage(), e);
				}
			}
		}
	}

	volatile StepKind stepKind;
	volatile EObject stepCaller;
	
	@Override
	/*
	 * This is the operation called from K3 code. We use this callback to pass the
	 * command to the generic executeOperation operation. (non-Javadoc)
	 * 
	 * @see fr.inria.diverse.k3.al.annotationprocessor.stepmanager.IStepManager#
	 * executeStep(java.lang.Object,
	 * fr.inria.diverse.k3.al.annotationprocessor.stepmanager.StepCommand,
	 * java.lang.String)
	 */
	public void executeStep(Object caller, final StepCommand command, String className, String methodName) {
		if (continueSimulation) {
			EObject eObj = (EObject) caller;
			ICompositeNode node = NodeModelUtils.getNode(eObj);
			if (node != null && breakpoints.containsKey(node.getStartLine())) {
				continueSimulation = false;
				updateVariables();
				sendStopEvent(StopReason.REACHED_BREAKPOINT);
				increment(finishDoStepSemaphore);
				decrement(startDoStepSemaphore);
				stepCaller = eObj;
			}
		} else { // Execution is paused
			EObject eObj = (EObject) caller;
			EObject debugObj = eObj;
			String debugString = "";
			while (debugObj != null && baseStream != null) {
				baseStream.println(debugString + debugObj.toString());
				debugString += "    ";
				debugObj = debugObj.eContainer();
			}
		    switch(stepKind) {
		        case STEP_IN:
		        	// Break at each step
		        	increment(finishDoStepSemaphore); // give control back to the caller.
				    //wait for stuff to be done in the server before to continue
				    decrement(startDoStepSemaphore); //waiting for the caller to do something
				    stepCaller = eObj;
				    break;
		        	
		        case NEXT:
		        	// Only break if not child
		        	boolean foundNext = false;
		        	EObject containerNext = eObj.eContainer();
		        	while (containerNext != null && !foundNext) {
		        		if (containerNext == stepCaller) {
		        			foundNext = true;
		        		}
		        		containerNext = containerNext.eContainer();
		        	}
		        	if (!foundNext) {
		        		increment(finishDoStepSemaphore); // give control back to the caller.
					    //wait for stuff to be done in the server before to continue
					    decrement(startDoStepSemaphore); //waiting for the caller to do something
			        	stepCaller = eObj;
		        	}
		        	break;
		        	
		        case STEP_OUT:
		        	// Only break if not child nor sibling
		        	boolean foundStepOut = false;
		        	EObject containerStepOut = eObj.eContainer();
		        	if (containerStepOut != stepCaller.eContainer()) {
			        	while (containerStepOut != null && !foundStepOut) {
			        		if (containerStepOut == stepCaller) {
			        			foundStepOut = true;
			        		}
			        		containerStepOut = containerStepOut.eContainer();
			        	}
		        	}
		        	if (!foundStepOut) {
		        		increment(finishDoStepSemaphore); // give control back to the caller.
					    //wait for stuff to be done in the server before to continue
					    decrement(startDoStepSemaphore); //waiting for the caller to do something
			        	stepCaller = eObj;
		        	}
		        	break;
		    }
		}

		if (stopReceived) {
			throw new StopSimulationException();
		}
		
		command.execute();
		
//		executeOperation(caller, className, methodName, new Runnable() {
//			@Override
//			public void run() {
//				command.execute();
//			}
//		});
	}

	
	@Override
	/*
	 * This is the operation used to act as a StepManager in K3. We return true if
	 * we have the same editing domain as the object. (non-Javadoc)
	 * 
	 * @see fr.inria.diverse.k3.al.annotationprocessor.stepmanager.IStepManager#
	 * canHandle (java.lang.Object)
	 */
	public boolean canHandle(Object caller) {
		if (caller instanceof EObject) {
			EObject eObj = (EObject) caller;
			org.eclipse.emf.transaction.TransactionalEditingDomain editingDomain = getEditingDomain(eObj);
			return editingDomain == this.editingDomain;

		}
		return false;
	}

	/**
	 * Return a bundle containing 'aspectClassName'.
	 * 
	 * Return null if not found.
	 */
	private Bundle findBundle(final GenericModelExecutionContext<ISequentialRunConfiguration> executionContext, String aspectClassName) {

		// Look using JavaWorkspaceScope as this is safer and will look in
		// dependencies
		IType mainIType = getITypeMainByWorkspaceScope(aspectClassName);

		Bundle bundle = null;
		String bundleName = null;
		if (mainIType != null) {
			IPackageFragmentRoot packageFragmentRoot = (IPackageFragmentRoot) mainIType.getPackageFragment()
					.getParent();

			bundleName = packageFragmentRoot.getPath().removeLastSegments(1).lastSegment().toString();
			if (bundleName != null) {

				// We try to look into an already loaded bundle
				bundle = Platform.getBundle(bundleName);
			}
		} else {
			// the main isn't visible directly from the workspace, try another
			// method
			bundle = _executionContext.getDslBundle();
		}

		return bundle;
	}

	/**
	 * search the bundle that contains the Main class. The search is done in the
	 * workspace scope (ie. if it is defined in the current workspace it will find
	 * it
	 * 
	 * @return the name of the bundle containing the Main class or null if not found
	 */
	private IType getITypeMainByWorkspaceScope(String className) {
		SearchPattern pattern = SearchPattern.createPattern(className, IJavaSearchConstants.CLASS,
				IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();

		final List<IType> binaryType = new ArrayList<IType>();

		SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				binaryType.add((IType) match.getElement());
			}
		};
		SearchEngine engine = new SearchEngine();

		try {
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope,
					requestor, null);
		} catch (CoreException e1) {
			throw new RuntimeException("Error while searching the bundle: " + e1.getMessage());
			// return new Status(IStatus.ERROR, Activator.PLUGIN_ID, );
		}

		return binaryType.isEmpty() ? null : binaryType.get(0);
	}

	private static TransactionalEditingDomain getEditingDomain(EObject o) {
		return getEditingDomain(o.eResource().getResourceSet());
	}

	private static InternalTransactionalEditingDomain getEditingDomain(ResourceSet rs) {
		TransactionalEditingDomain edomain = org.eclipse.emf.transaction.TransactionalEditingDomain.Factory.INSTANCE
				.getEditingDomain(rs);
		if (edomain instanceof InternalTransactionalEditingDomain)
			return (InternalTransactionalEditingDomain) edomain;
		else
			return null;
	}

	/**
	 * Load the model for the given URI
	 * 
	 * @param modelURI
	 *            to load
	 * @return the loaded resource
	 */
	public static Resource loadModel(URI modelURI) {
		Resource resource = null;
		ResourceSet resourceSet;
		resourceSet = new ResourceSetImpl();
		resource = resourceSet.createResource(modelURI);
		try {
			resource.load(null);
		} catch (IOException e) {
			// chut
		}
		return resource;
	}
	
	// semaphore for locking doStep
	Semaphore startDoStepSemaphore;
	Semaphore finishDoStepSemaphore;
	volatile boolean simulationEnded;
	volatile boolean continueSimulation;
	volatile boolean stopReceived;

	private void decrement(Semaphore sem) {
		try {
			sem.acquire();
		} catch (InterruptedException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}
	
	private void increment(Semaphore sem) {
			sem.release();
	}
	
	AtomicDouble lastknownTime = new AtomicDouble(-1.0);
	double timeBeforeDoStep = -1;

	private StopCondition lastStopcondition = null;

	public static final int GEMOC_PORT = 39635;
	// socket for communicating with the simulation thread
	ServerSocket serverSocket = null;
	
	ObjectOutputStream cout = null;

	protected void simulate() throws Exception {
		//used to retrieve exceptions from the solverThread
		BlockingQueue<Exception> exceptionQueue = new ArrayBlockingQueue<>(1);

		updateVariables();
		
		Thread solverThread = launchEntryPointInThread(exceptionQueue);

		solverThread.start();
		
		serverSocket = new ServerSocket(GEMOC_PORT);

		Socket clientSocket = serverSocket.accept();
		
		// OutputStream needs to be created before InputStream or the client will deadlock
		cout = new ObjectOutputStream(clientSocket.getOutputStream());
		ObjectInputStream cin = new ObjectInputStream(clientSocket.getInputStream());
		
		Object clientCommand;
		do {
			clientCommand = cin.readObject();
			System.out.println("Command received: "+clientCommand);
			if (clientCommand instanceof DoStepCommand) {
				timeBeforeDoStep = lastknownTime.doubleValue();
				System.out.println("DoStep starts @"+timeBeforeDoStep);
				//currentPredicate = ((DoStepCommand) clientCommand).predicate;
				//StopCondition stopCond = this.doStep(currentPredicate);
				stepKind = ((DoStepCommand) clientCommand).stepKind;
				StopCondition stopCond = this.doStep();
				//System.out.println("DoStep stops @"+stopCond.timeValue+" due to "+stopCond.stopReason);
				System.out.println("DoStep stops due to "+stopCond.stopReason);
				cout.writeObject(stopCond);
			} else if (clientCommand instanceof ListVariablesCommand) {
				cout.writeObject(new ArrayList<String>(this.variables.keySet()));
			} else if (clientCommand instanceof GetVariableCommand) {
				String varQN = ((GetVariableCommand) clientCommand).variableQualifiedName;
				Object varValue= this.variables.get(varQN);
				cout.writeObject(varValue.toString());
			} else if (clientCommand instanceof StopCommand) {
				stopReceived = true;
				increment(startDoStepSemaphore);
				decrement(finishDoStepSemaphore);
			} else if (clientCommand instanceof ClearBreakpointsCommand) {
				breakpoints.clear();
			} else if (clientCommand instanceof ToggleBreakpointCommand) {
				int breakpointLine = ((ToggleBreakpointCommand) clientCommand).line;
				if (breakpoints.containsKey(breakpointLine)) {
					breakpoints.put(breakpointLine, !breakpoints.get(breakpointLine));
				} else {
					breakpoints.put(breakpointLine, true);
				}
			} else if (clientCommand instanceof ContinueCommand) {
				continueSimulation = true;
				increment(startDoStepSemaphore);
				decrement(finishDoStepSemaphore);
			}
			System.out.println("wait for a new command.");
		} while(!simulationEnded);
		
		solverThread.join();

		if (stopReceived) {
			cout.writeObject(Boolean.TRUE);
		}
		clientSocket.close();
		serverSocket.close();
		
		if (exceptionQueue.isEmpty()) {
			return;
		} else {
			throw exceptionQueue.remove();
		}
	}

	PrintStream baseStream;
	ModelAwarePrintStream printStream;
	Thread simulationThread;
	
	public Thread launchEntryPointInThread(BlockingQueue<Exception> exceptionQueue) {
		baseStream = System.out;
		printStream = new ModelAwarePrintStream(outputStream, baseStream);

		Thread simulationThread = new Thread( () -> 
			// since aspect's methods are static, first arg is null
			{
				
				simulationEnded = false;
				try {
					this.entryPointMethod.invoke(null, entryPointMethodParameters.get(0));
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					if (e.getCause() instanceof StopSimulationException) {
						baseStream.println("Simulation stopped");
					} else {
						exceptionQueue.offer(e);
					}
				}
				if (continueSimulation) {
					sendStopEvent(StopReason.REACHED_SIMULATION_END);
				}
				updateVariables();
				simulationEnded = true;
				printStream.close();
				System.setOut(baseStream);
				increment(finishDoStepSemaphore);
			}
		);
		printStream.registerModelExecutionThread(simulationThread);
		System.setOut(printStream);
		return simulationThread;
	}
	
	public void sendStopEvent(StopReason stopReason) {
		StopEvent stopEvent = new StopEvent();
		stopEvent.stopReason = stopReason;
		try {
			cout.writeObject(stopEvent);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//public StopCondition doStep(CoordinationPredicate predicate) {
	public StopCondition doStep() {	
		increment(startDoStepSemaphore);
		decrement(finishDoStepSemaphore);

		updateVariables();
		
		if (simulationEnded) {
			lastStopcondition = new StopCondition(StopReason.REACHED_SIMULATION_END);
		} else {
			lastStopcondition = new StopCondition(StopReason.REACHED_NEXT_LOGICAL_STEP);
		}
		return lastStopcondition;
	}
	
	private void updateVariables() {
		for (MutableField mf : partAccessor.extractMutableField(root)) {
			variables.put(mf.getName(), mf.getValue());
		}
	}
	
	public List<StackFrame> getStackTrace() {
		List<StackFrame> stackTrace = new ArrayList<>();
		
		EObject eObj = stepCaller;
		while (eObj != null) {
			StackFrame frame = new StackFrame();
			frame.setName(eObj.toString());
			ICompositeNode node = NodeModelUtils.getNode(eObj);
			frame.setLine(node.getStartLine());
			stackTrace.add(0, frame);
			eObj = eObj.eContainer();
		}
		
		return stackTrace;
	}
	
	public String getSourceContent() {
		ICompositeNode node = NodeModelUtils.getNode(root);
		return node.getText();
	}
	
}
