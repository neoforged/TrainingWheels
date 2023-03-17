package net.minecraftforge.trainingwheels.gradle.functional.util

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger

import java.util.function.Consumer

@CompileStatic
class LoggerWriter extends Writer {

    private final Consumer<String> writer

    LoggerWriter(Consumer<String> writer) {
        this.writer = writer
    }

    @CompileDynamic
    LoggerWriter(Logger logger, Level level) {
        final lvl = level.name().toLowerCase(Locale.ROOT)
        this.writer = logger.&"$lvl"
    }

    @Override
    void write(@NotNull char[] cbuf, int off, int len) throws IOException {
        writer.accept(new String(cbuf, off, len).trim())
    }

    @Override
    void flush() throws IOException {
    }

    @Override
    void close() throws IOException {
    }

    static enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
