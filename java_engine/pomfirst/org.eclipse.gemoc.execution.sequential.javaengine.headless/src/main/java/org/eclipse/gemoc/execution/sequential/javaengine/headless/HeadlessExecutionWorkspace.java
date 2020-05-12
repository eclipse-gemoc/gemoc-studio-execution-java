package org.eclipse.gemoc.execution.sequential.javaengine.headless;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionWorkspace;

public class HeadlessExecutionWorkspace implements IExecutionWorkspace {

	@Override
	public IPath getProjectPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IPath getModelPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IPath getMoCPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IPath getMSEModelPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IPath getExecutionPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void copyFileToExecutionFolder(IPath filePath) throws CoreException {
		// TODO Auto-generated method stub

	}

}
