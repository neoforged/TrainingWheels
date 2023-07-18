/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.trainingwheels.base.file;

import com.google.common.jimfs.Jimfs;

import java.nio.file.FileSystem;

/**
 * Utility class for creating {@link PathFile} instances, with particular properties.
 */
public final class FileTestingUtils {

    private FileTestingUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: FileTestingUtils. This is a utility class");
    }

    /**
     * Creates a new {@link PathFile} instance, with a {@link FileSystem} that is backed
     * in memory. The {@link FileSystem} is created using {@link Jimfs#newFileSystem()}.
     *
     * @param path the path to the file, relative to the root of the file system
     * @return the new {@link PathFile} instance
     */
    public static PathFile newSimpleTestFile(String path) {
        final FileSystem fileSystem = Jimfs.newFileSystem();
        return PathFile.newInstance(fileSystem.getPath(path));
    }
}
