/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.trainingwheels.gradle.functional.builder

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.apache.tools.ant.util.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Consumer
import java.util.stream.Collectors

@CompileStatic
class Runtime {

    private final String projectName
    private final Map<String, String> properties = Maps.newHashMap()
    private final Set<String> jvmArgs = Sets.newHashSet()
    private final boolean usesLocalBuildCache
    private final boolean debugBuildCache
    private final boolean usesConfigurationCache
    private final boolean enableBuildScan
    private final Map<String, String> files
    private final Map<String, String> plugins
    private final Map<String, String> settingsPlugins
    private final boolean retainBuildDirectoryBetweenRuns

    private File projectDir
    private Runtime rootProject

    Runtime(String projectName, Map<String, String> properties, final Set<String> jvmArgs, boolean usesLocalBuildCache, boolean debugBuildCache, boolean usesConfigurationCache, boolean enableBuildScan, Map<String, String> files, Map<String, String> plugins, Map<String, String> settingsPlugins, boolean retainBuildDirectoryBetweenRuns) {
        this.projectName = projectName
        this.usesLocalBuildCache = usesLocalBuildCache
        this.debugBuildCache = debugBuildCache
        this.usesConfigurationCache = usesConfigurationCache
        this.enableBuildScan = enableBuildScan
        this.files = files

        this.properties.put('org.gradle.console', 'rich')
        this.properties.putAll(properties)

        this.jvmArgs.addAll(jvmArgs)
        this.plugins = plugins
        this.settingsPlugins = settingsPlugins
        this.retainBuildDirectoryBetweenRuns = retainBuildDirectoryBetweenRuns
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
            setupThisAsChild(rootProject, rootProject.projectDir)
        }
    }

    private void setupThisAsChild(final Runtime rootProject, final File workspaceDirectory) {
        this.projectDir = new File(workspaceDirectory, this.projectName.replace(":", "/"))
        this.projectDir.mkdirs()
        this.rootProject = rootProject

        final File settingsFile = new File(rootProject.projectDir, "settings.gradle")
        settingsFile << "include '${this.projectName.replace(":", "/")}'\n"

        setupThis()
    }

    private void setupThisAsRoot(final File workspaceDirectory) {
        this.projectDir = workspaceDirectory
        this.rootProject = this

        final File settingsFile = new File(this.projectDir, "settings.gradle")
        settingsFile.getParentFile().mkdirs()

        settingsFile << 'plugins {\n'
        settingsPlugins.keySet().forEach { pluginId ->
            final String version = settingsPlugins.get(pluginId)
            String line = "   id '${pluginId}'"
            if (!version.isEmpty()) {
                line += " version '${version}'"
            }

            settingsFile << line + "\n"
        }
        settingsFile << '   id "com.gradle.develocity" version "3.17"'
        settingsFile << '} \n\n'

        if (this.usesLocalBuildCache) {
            final File localBuildCacheDirectory = new File(this.projectDir, "build-cache")
            settingsFile << """
                buildCache {
                    local {
                        directory = new File('${localBuildCacheDirectory.absoluteFile.toString()}')
                    }
                } \n\n
            """
        }

        settingsFile << "rootProject.name = '${this.projectName}'\n\n"

        if (enableBuildScan) {
            settingsFile << '''
            develocity {
                buildScan {
                    termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
                    termsOfUseAgree = "yes"
                }
            }\n\n
            '''
        }

        if (this.usesConfigurationCache) {
            settingsFile << 'enableFeaturePreview "STABLE_CONFIGURATION_CACHE" \n\n'
        }

        setupThis()
    }

    private void setupThis() {
        final File propertiesFile = new File(this.projectDir, 'gradle.properties')
        propertiesFile.getParentFile().mkdirs()

        if (this.usesLocalBuildCache) {
            this.properties.put('org.gradle.caching', 'true')

            if (this.debugBuildCache) {
                this.properties.put('org.gradle.caching.debug', 'true')
            }
        }

        if (this.usesConfigurationCache) {
            this.properties.put('org.gradle.configuration-cache', 'true')
        }

        this.properties.put('org.gradle.jvmargs', String.join(" ", this.jvmArgs))
        Files.write(propertiesFile.toPath(), this.properties.entrySet().stream().map { e -> "${e.getKey()}=$e.value".toString() }.collect(Collectors.toList()), StandardOpenOption.CREATE_NEW)

        final File buildGradleFile = new File(this.projectDir, 'build.gradle')
        if (!plugins.isEmpty()) {
            buildGradleFile << 'plugins {\n'
            plugins.keySet().forEach { pluginId ->
                final String version = plugins.get(pluginId)
                String line = "   id '${pluginId}'"
                if (!version.isEmpty()) {
                    line += " version: '${version}'"
                }

                buildGradleFile << line + "\n"
            }
            buildGradleFile << '} \n\n'
        }

        for (final def e in this.files.entrySet()) {
            final String file = e.getKey()
            final String content = e.getValue()

            final File target = new File(this.projectDir, file)
            target.getParentFile().mkdirs()
            target << content
        }
    }

    RunResult run(final Consumer<RunBuilder> runBuilderConsumer) {
        if (this.rootProject != null && this.rootProject != this)
            throw new IllegalStateException("Tried to run none root build!")

        final RunBuilder runBuilder = new RunBuilder()
        runBuilderConsumer.accept(runBuilder)

        final GradleRunner runner = gradleRunner()

        if (runBuilder.debug) {
            runner.withDebug(true)
        }

        final List<String> arguments = Lists.newArrayList(runBuilder.arguments)
        if (runBuilder.logLevel.isRequiredAsArgument) {
            arguments.addAll(runBuilder.logLevel.getArgument())
        }

        arguments.addAll(runBuilder.tasks)

        if (runBuilder.stacktrace) {
            arguments.add("--stacktrace")
        }

        if (!retainBuildDirectoryBetweenRuns) {
            Files.walkFileTree(projectDir.toPath(), new FileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName().toString() == "build") {
                        Files.walk(dir)
                                .sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete)

                        return FileVisitResult.SKIP_SUBTREE
                    }

                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE
                }
            })
        }

        if (runBuilder.shouldFail) {
            return new Result(runner.withArguments(arguments).buildAndFail(), this)
        } else {
            return new Result(runner.withArguments(arguments).build(), this)
        }
    }

    String getProjectName() {
        return projectName
    }

    File getProjectDir() {
        return projectDir
    }

    static class Builder {
        private final String projectName

        private final Map<String, String> properties = Maps.newHashMap()
        private final Set<String> jvmArgs = Sets.newHashSet('-Xmx8000m')
        private final Map<String, String> files = Maps.newHashMap()
        private final Map<String, String> plugins = Maps.newHashMap()
        private final Map<String, String> settingsPlugins = Maps.newHashMap()

        private boolean usesLocalBuildCache = false
        private boolean debugBuildCache = false
        private boolean usesConfigurationCache = false
        private boolean enableBuildScan = false

        private boolean retainBuildDirectory = false

        Builder(String projectName) {
            this.projectName = projectName
        }

        Builder enableLocalBuildCache() {
            this.usesLocalBuildCache = true
            return this
        }

        Builder debugBuildCache() {
            this.debugBuildCache = true
            return this
        }

        Builder enableConfigurationCache() {
            this.usesConfigurationCache = true
            return this
        }

        Builder enableBuildScan() {
            this.enableBuildScan = true
            return this
        }

        Builder retainBuildDirectoryBetweenRuns() {
            this.retainBuildDirectory = true
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
            this.plugin(pluginId, "")
        }

        Builder plugin(final String pluginId, final String pluginVersion) {
            this.plugins.put(pluginId, pluginVersion)
            return this
        }

        Builder settingsPlugin(final String pluginId) {
            this.settingsPlugin(pluginId, "")
        }

        Builder settingsPlugin(final String pluginId, final String pluginVersion) {
            this.settingsPlugins.put(pluginId, pluginVersion)
            return this
        }

        Builder withToolchains() {
            this.settingsPlugin("org.gradle.toolchains.foojay-resolver-convention", "0.4.0")
        }

        Runtime create() {
            return new Runtime(this.projectName, this.properties, this.jvmArgs, this.usesLocalBuildCache, this.debugBuildCache, this.usesConfigurationCache, this.enableBuildScan, this.files, this.plugins, this.settingsPlugins, this.retainBuildDirectory)
        }
    }

    static class RunBuilder {
        private LogLevel logLevel = LogLevel.NONE
        private final List<String> arguments = new ArrayList<>()
        private final List<String> tasks = new ArrayList<>()
        private boolean shouldFail = false
        private boolean debug = false
        private boolean stacktrace = false

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

        RunBuilder debug() {
            this.debug = true
            return this
        }

        RunBuilder stacktrace() {
            this.stacktrace = true;
            return this;
        }
    }

    static enum LogLevel {
        NONE(),
        INFO('info'),
        DEBUG('debug');

        private final String argument
        private final boolean isRequiredAsArgument

        LogLevel(String argument = '') {
            if (argument == '') {
                isRequiredAsArgument = false
                this.argument = ''
                return
            }

            this.isRequiredAsArgument = true
            this.argument = argument
        }

        String getArgument() {
            if (!isRequiredAsArgument)
                throw new IllegalStateException("No argument is needed.")

            return "--$argument"
        }
    }

    private class Result implements RunResult {

        private final BuildResult result;
        private final Runtime runtime;

        Result(BuildResult result, Runtime runtime) {
            this.result = result
            this.runtime = runtime
        }

        @Override
        File file(String path) {
            return new File(runtime.projectDir, path)
        }

        @Override
        String getOutput() {
            return result.getOutput()
        }

        @Override
        List<BuildTask> getTasks() {
            return result.getTasks()
        }

        @Override
        List<BuildTask> tasks(TaskOutcome outcome) {
            return result.tasks(outcome)
        }

        @Override
        List<String> taskPaths(TaskOutcome outcome) {
            return result.taskPaths(outcome)
        }

        @Override
        BuildTask task(String taskPath) {
            return result.task(taskPath)
        }
    }

    /**
     * Represents the result of a build run.
     */
    interface RunResult extends BuildResult {

        /**
         * Returns a file from the project.
         *
         * @param path The path to the file.
         * @return The file.
         */
        File file(String path);
    }
}
