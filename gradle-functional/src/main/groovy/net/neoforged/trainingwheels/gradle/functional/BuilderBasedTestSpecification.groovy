/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.trainingwheels.gradle.functional

import com.google.common.collect.Maps
import net.neoforged.trainingwheels.gradle.functional.builder.Runtime
import spock.lang.Specification
import spock.lang.TempDir

import java.util.function.Consumer

abstract class BuilderBasedTestSpecification extends Specification {

    @TempDir
    protected File tempDir
    protected File projectDirectory

    private boolean registeredRuntimesAreConfigured = false
    private Map<Runtime, Runtime> roots = Maps.newHashMap()
    private Map<String, Runtime> runtimes = Maps.newHashMap()

    protected String pluginUnderTest;
    protected boolean injectIntoAllProject;
    protected boolean injectIntoRootProject

    protected void configurePluginUnderTest() {
        pluginUnderTest = null;
        injectIntoAllProject = false;
        injectIntoRootProject = false;
    }

    protected File getTestTempDirectory() {
        return tempDir;
    }

    def setup() {
        configurePluginUnderTest()

        // The @TempDir is already per-test, unless explicitly @Shared
        this.projectDirectory = getTestTempDirectory()
        this.registeredRuntimesAreConfigured = true
        runtimes.values().forEach {runtime -> {
            final Runtime root = this.roots.get(runtime)
            final File workingDirectory = new File(projectDirectory, root.getProjectName())
            runtime.setup(root, workingDirectory)
        }}
    }

    protected Runtime create(final String name, final Consumer<Runtime.Builder> builderConsumer) {
        final Runtime.Builder builder = new Runtime.Builder(name)
        builderConsumer.accept(builder)

        if (pluginUnderTest != null && !pluginUnderTest.isEmpty() && (injectIntoRootProject || injectIntoAllProject)) {
            builder.plugin(pluginUnderTest)
        }

        final Runtime runtime = builder.create()
        return registerProjectFrom(runtime, name, runtime)
    }

    protected Runtime create(final String root, String name, final Consumer<Runtime.Builder> builderConsumer) {
        if (!this.runtimes.containsKey(root))
            throw new IllegalStateException("No runtime with name: $root")

        final Runtime rootRuntime = this.runtimes.get(root)

        return create(rootRuntime, name, builderConsumer)
    }

    protected Runtime create(final Runtime rootRuntime, String name, final Consumer<Runtime.Builder> builderConsumer) {
        final Runtime.Builder builder = new Runtime.Builder(name)
        builderConsumer.accept(builder)

        if (pluginUnderTest != null && !pluginUnderTest.isEmpty() && injectIntoAllProject) {
            builder.plugin(pluginUnderTest)
        }

        final Runtime runtime = builder.create()
        return registerProjectFrom(runtime, name, rootRuntime)
    }

    private Runtime registerProjectFrom(Runtime runtime, String name, Runtime rootRuntime) {
        if (this.registeredRuntimesAreConfigured) {
            final File workingDirectory = new File(projectDirectory, name)
            runtime.setup(runtime, workingDirectory)
        }

        this.runtimes.put(name, runtime)
        this.roots.put(runtime, rootRuntime)
        return runtime
    }
}
