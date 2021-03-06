// CloudCoder - a web-based pedagogical programming environment
// Copyright (C) 2011-2012, Jaime Spacco <jspacco@knox.edu>
// Copyright (C) 2011-2012, David H. Hovemeyer <david.hovemeyer@gmail.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.cloudcoder.builder2.javaprogram;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.cloudcoder.builder2.model.BuilderSubmission;
import org.cloudcoder.builder2.model.BytecodeExecutable;
import org.cloudcoder.builder2.model.Command;
import org.cloudcoder.builder2.model.CommandInput;
import org.cloudcoder.builder2.model.IBuildStep;
import org.cloudcoder.builder2.model.InternalBuilderException;

/**
 * Create {@link Command}s to run a Java program ({@link BytecodeExecutable})
 * for each {@link CommandInput}.
 * 
 * @author David Hovemeyer
 */
public class JavaProgramToCommandForEachCommandInputBuildStep implements IBuildStep {

	@Override
	public void execute(BuilderSubmission submission, Properties config) {
		BytecodeExecutable bytecodeExe = submission.getArtifact(BytecodeExecutable.class);
		if (bytecodeExe == null) {
			throw new InternalBuilderException(this.getClass(), "No BytecodeExecutable");
		}
		
		CommandInput[] commandInputList = submission.getArtifact(CommandInput[].class);
		if (commandInputList == null) {
			throw new InternalBuilderException(this.getClass(), "No CommandInput list");
		}
		
		Command[] commandList = new Command[commandInputList.length];
		
		for (int i = 0; i < commandInputList.length; i++) {
			List<String> arguments = new ArrayList<String>();
			arguments.add("java");
			arguments.add("-classpath");
			arguments.add(".");
			arguments.add(bytecodeExe.getMainClass());
			commandList[i] = new Command(bytecodeExe.getDir(), arguments);
		}
		
		submission.addArtifact(commandList);
	}

}
