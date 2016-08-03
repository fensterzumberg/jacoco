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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jacoco.core.internal.Java9Support;
import org.jacoco.core.internal.data.CRC64;
import org.jacoco.core.internal.flow.ClassProbesAdapter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.jacoco.core.runtime.SystemPropertiesRuntime;
import org.jacoco.core.test.TargetLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public class InterfaceFieldProbeArrayStrategyTest {

	private final IRuntime runtime = new SystemPropertiesRuntime();

	@Before
	public void setup() throws Exception {
		runtime.startup(new RuntimeData());
	}

	@After
	public void teardown() {
		runtime.shutdown();
	}

	/**
	 * This test demonstrates that {@link InterfaceFieldProbeArrayStrategy} not
	 * suitable for classes.
	 */
	@Test
	public void bad_cycles() throws Exception {
		{
			final TargetLoader targetLoader = new TargetLoader();
			targetLoader.add(Base.class);
			Class target = targetLoader.add(BadClassCyclesTarget.class);
			target.newInstance();
		}

		final TargetLoader targetLoader = new TargetLoader();
		targetLoader.add(Base.class);
		final Class target = targetLoader.add(
				BadClassCyclesTarget.class.getName(),
				instrument(BadClassCyclesTarget.class));

		try {
			target.newInstance();
			fail("ExceptionInInitializerError expected");
		} catch (ExceptionInInitializerError e) {
			assertTrue(e.getCause() instanceof NullPointerException);
		}
	}

	private byte[] instrument(Class cls) throws Exception {
		final byte[] bytes = TargetLoader.getClassDataAsBytes(cls);
		final boolean java9 = Java9Support.isPatchRequired(bytes);

		final ClassReader reader = new ClassReader(
				java9 ? Java9Support.downgrade(bytes) : bytes);

		final long classId = CRC64.checksum(reader.b);

		final ProbeCounter counter = new ProbeCounter();
		reader.accept(new ClassProbesAdapter(counter, false), 0);

		final IProbeArrayStrategy strategy = new InterfaceFieldProbeArrayStrategy(
				reader.getClassName(), classId, counter.getCount(), runtime);

		final ClassWriter writer = new ClassWriter(reader, 0);
		final ClassVisitor visitor = new ClassProbesAdapter(
				new ClassInstrumenter(strategy, writer), true);
		reader.accept(visitor, ClassReader.EXPAND_FRAMES);

		final byte[] instrumented = writer.toByteArray();
		if (java9) {
			Java9Support.upgrade(instrumented);
		}
		return instrumented;
	}

	public static class BadClassCyclesTarget extends Base {
		static {
			System.out.println("Target.cinit");
		}

		public BadClassCyclesTarget() {
			System.out.println("Target.init");
		}

		void someMethod() {
			System.out.println("Target.someMethod");
		}
	}

	public static class Base {
		static final BadClassCyclesTarget b = new BadClassCyclesTarget();

		static {
			b.someMethod();
		}
	}

}
