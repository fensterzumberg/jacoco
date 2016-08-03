/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.instr;

import org.jacoco.core.runtime.IExecutionDataAccessorGenerator;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * This strategy for Java 8 interfaces adds a static field to hold the probe
 * array and adds code for its initialization into class initialization method.
 */
class InterfaceFieldProbeArrayStrategy implements IProbeArrayStrategy {

	private final String className;
	private final long classId;
	private final int probeCount;
	private final IExecutionDataAccessorGenerator accessorGenerator;

	private boolean seenClinit = false;

	InterfaceFieldProbeArrayStrategy(final String className, final long classId,
			final int probeCount,
			final IExecutionDataAccessorGenerator accessorGenerator) {
		this.className = className;
		this.classId = classId;
		this.probeCount = probeCount;
		this.accessorGenerator = accessorGenerator;
	}

	public int storeInstance(final MethodVisitor mv, final boolean clinit,
			final int variable) {
		if (clinit) {
			final int maxStack = accessorGenerator.generateDataAccessor(classId,
					className, probeCount, mv);

			// Stack[0]: [Z

			mv.visitInsn(Opcodes.DUP);

			// Stack[1]: [Z
			// Stack[0]: [Z

			mv.visitFieldInsn(Opcodes.PUTSTATIC, className,
					InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);

			// Stack[0]: [Z

			mv.visitVarInsn(Opcodes.ASTORE, variable);

			seenClinit = true;
			return Math.max(maxStack, 2);
		} else {
			mv.visitFieldInsn(Opcodes.GETSTATIC, className,
					InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);

			// Stack[0]: [Z

			mv.visitVarInsn(Opcodes.ASTORE, variable);

			return 1;
		}
	}

	public void addMembers(final ClassVisitor cv, final int probeCount) {
		createDataField(cv);
		if (!seenClinit) {
			createInitMethod(cv, probeCount);
		}
	}

	private void createDataField(final ClassVisitor cv) {
		cv.visitField(InstrSupport.DATAFIELD_INTF_ACC,
				InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC, null,
				null);
	}

	private void createInitMethod(final ClassVisitor cv, final int probeCount) {
		final MethodVisitor mv = cv.visitMethod(InstrSupport.INITMETHOD_ACC,
				InstrSupport.CLINIT_NAME, InstrSupport.CLINIT_DESC, null, null);
		mv.visitCode();

		final int maxStack = accessorGenerator.generateDataAccessor(classId,
				className, probeCount, mv);

		// Stack[0]: [Z

		mv.visitFieldInsn(Opcodes.PUTSTATIC, className,
				InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);

		mv.visitInsn(Opcodes.RETURN);

		mv.visitMaxs(maxStack, 0);
		mv.visitEnd();
	}

}
