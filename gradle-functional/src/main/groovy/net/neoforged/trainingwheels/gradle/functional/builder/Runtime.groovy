/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.trainingwheels.gradle.functional.builder

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import java.util.stream.Collectors

@CompileStatic
class Runtime {

    private final String projectName
    private final Map<String, String> properties = Maps.newHashMap()
    private final Set<String> jvmArgs = Sets.newHashSet();
    private final boolean usesLocalBuildCache
    private final Map<String, String> files
    private final Map<String, String> plugins;

    private File projectDir
    private Runtime rootProject

    Runtime(String projectName, Map<String, String> properties, final Set<String> jvmArgs, boolean usesLocalBuildCache, Map<String, String> files, Map<String, String> plugins) {
        this.projectName = projectName
        this.usesLocalBuildCache = usesLocalBuildCache
        this.files = files

        this.properties.put('org.gradle.console', 'rich')
        this.properties.putAll(properties)

        this.jvmArgs.addAll(jvmArgs);
        this.plugins = plugins
    }

    private GradleRunner gradleRunner() {
        def runner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .forwardOutput()

        return runner
    }

    void setup(final Runtime rootProject, final File workspaceDirectory) {
        if (rootProject == this) {
            setupThisAsRoot(workspaceDirectory)
        } else {
            setupThisAsChild(rootProject, workspaceDirectory)
        }
    }

    private void setupThisAsChild(final Runtime rootProject, final File workspaceDirectory) {
        this.projectDir = new File(workspaceDirectory, this.projectName.replace(":", "/"))
        this.projectDir.mkdirs()
        this.rootProject = rootProject

        setupThis()
    }

    private void setupThisAsRoot(final File workspaceDirectory) {
        this.projectDir = workspaceDirectory
        this.rootProject = this

        final File settingsFile = new File(this.projectDir, "settings.gradle")
        settingsFile.getParentFile().mkdirs()

        if (this.usesLocalBuildCache) {
            final File localBuildCacheDirectory = new File(this.projectDir, "cache/build")
            settingsFile << """
                buildCache {
                    local {
                        directory '${localBuildCacheDirectory.toURI()}'
                    }
                } \n\n
            """
        }

        settingsFile << "rootProject.name = '${this.projectName}'"

        setupThis()
    }

    private void setupThis() {
        final File propertiesFile = new File(this.projectDir, 'gradle.properties')
        propertiesFile.getParentFile().mkdirs()

        this.properties.put('org.gradle.jvmargs', String.join(" ", this.jvmArgs))
        Files.write(propertiesFile.toPath(), this.properties.entrySet().stream().map {e -> "${e.getKey()}=$e.value".toString() }.collect(Collectors.toList()), StandardOpenOption.CREATE_NEW)

        final File buildGradleFile = new File(this.projectDir, 'build.gradle');
        if (!plugins.isEmpty()) {
            buildGradleFile << 'plugins {\n'
            plugins.keySet().forEach {pluginId ->
                final String version = plugins.get(pluginId);
                String line = "   id '${pluginId}'"
                if (!version.isEmpty()) {
                    line += " version: '${version}'"
                }

                buildGradleFile << line + "\n";
            }
            buildGradleFile << '} \n\n'
        }

        for (final def e in this.files.entrySet() ) {
            final String file = e.getKey()
            final String content = e.getValue()

            final File target = new File(this.projectDir, file)
            target.getParentFile().mkdirs()
            target << content
        }
    }

    BuildResult run(final Consumer<RunBuilder> runBuilderConsumer) {
        if (this.rootProject != null && this.rootProject != this)
            throw new IllegalStateException("Tried to run none root build!")

        final RunBuilder runBuilder = new RunBuilder()
        runBuilderConsumer.accept(runBuilder)

        final GradleRunner runner = gradleRunner()

        final List<String> arguments = Lists.newArrayList(runBuilder.arguments)
        if (runBuilder.logLevel.isRequiredAsArgument)
            arguments.addAll(runBuilder.logLevel.getArgument())
        arguments.addAll(runBuilder.tasks)

        if (runBuilder.shouldFail) {
            return runner.withArguments(arguments).buildAndFail()
        } else {
            return runner.withArguments(arguments).build()
        }
    }

    String getProjectName() {
        return projectName
    }

    static class Builder {
        private final String projectName

        private final Map<String, String> properties = Maps.newHashMap()
        private final Set<String> jvmArgs = Sets.newHashSet();
        private final Map<String, String> files = Maps.newHashMap()
        private final Map<String, String> plugins = Maps.newHashMap()

        private boolean usesLocalBuildCache = false

        Builder(String projectName) {
            this.projectName = projectName
        }

        Builder enableLocalBuildCache() {
            this.usesLocalBuildCache = true
            return this
        }

        Builder property(final String key, final String value) {
            this.properties.put(key, value)
            return this
        }

        Builder jvmArg(final String value) {
            this.jvmArgs.add(value)
            return this
        }

        Builder maxMemory(final String memoryNotation) {
            return jvmArg("-Xmx$memoryNotation")
        }

        Builder parallel() {
            return property('org.gradle.parallel', 'true')
        }

        Builder file(final String path, final String content) {
            this.files.put(path, content)
            return this
        }

        Builder build(final String content) {
            return this.file('build.gradle', content)
        }

        Builder settings(final String content) {
            return this.file("settings.gradle", content)
        }

        Builder plugin(final String pluginId) {
            this.plugin(pluginId, "");
        }

        Builder plugin(final String pluginId, final String pluginVersion) {
            this.plugins.put(pluginId, pluginVersion)
            return this;
        }

        Runtime create() {
            return new Runtime(this.projectName, this.properties, this.jvmArgs, this.usesLocalBuildCache, this.files, plugins)
        }
    }

    static class RunBuilder {
        private LogLevel logLevel = LogLevel.NONE
        private final List<String> arguments = new ArrayList<>()
        private final List<String> tasks = new ArrayList<>()
        private boolean shouldFail = false

        private RunBuilder() {
        }

        RunBuilder log(final LogLevel level) {
            this.logLevel = level
            return this
        }

        RunBuilder arguments(final String... args) {
            this.arguments.addAll(args)
            return this
        }

        RunBuilder tasks(final String... tsk) {
            this.tasks.addAll(tsk)
            return this
        }

        RunBuilder shouldFail() {
            this.shouldFail = true
            return this
        }
    }

    static enum LogLevel {
        NONE(),
        INFO('info'),
        DEBUG('debug');

        private final String argument
        private final boolean isRequiredAsArgument;

        LogLevel(String argument = '') {
            if (argument == '') {
                isRequiredAsArgument = false;
                this.argument = ''
                return;
            }

            this.isRequiredAsArgument = true;
            this.argument = argument
        }

        String getArgument() {
            if (!isRequiredAsArgument)
                throw new IllegalStateException("No argument is needed.")

            return "--$argument"
        }
    }
}
