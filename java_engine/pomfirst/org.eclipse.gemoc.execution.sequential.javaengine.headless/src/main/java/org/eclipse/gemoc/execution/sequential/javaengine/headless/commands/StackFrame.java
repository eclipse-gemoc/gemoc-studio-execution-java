package org.eclipse.gemoc.execution.sequential.javaengine.headless.commands;

public class StackFrame {
	
	private String name;
	private int line;
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public int getLine() {
		return line;
	}
	
	public void setLine(int line) {
		this.line = line;
	}

}
