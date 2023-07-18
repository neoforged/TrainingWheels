/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.trainingwheels.base.file;

import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.*;

public class PathFile extends File {

    private PathFile(String path) {
        super(path);
    }

    public FileSystem getFileSystem() {
        return toPath().getFileSystem();
    }

    @Override
    public Path toPath() {
        throw new IllegalArgumentException("toPath must be implemented!");
    }

    @Override
    public String getName() {
        return toPath().getFileName().toString();
    }

    @Override
    public boolean isFile() {
        return Files.isRegularFile(toPath());
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(toPath());
    }

    public static PathFile newInstance(Path path) {
        final PathFile file = Mockito.mock(PathFile.class, Answers.CALLS_REAL_METHODS);
        doReturn(path).when(file).toPath();
        return file;
    }

}
