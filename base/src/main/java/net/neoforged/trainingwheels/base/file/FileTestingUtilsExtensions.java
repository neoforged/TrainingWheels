/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.trainingwheels.base.file;

import com.google.common.jimfs.Jimfs;

import java.nio.file.FileSystem;

public class FileTestingUtilsExtensions {
    /**
     * Creates a new {@link PathFile} instance, with a {@link FileSystem} that is backed
     * in memory. The {@link FileSystem} is created using {@link Jimfs#newFileSystem()}.
     *
     * @param path the path to the file, relative to the root of the file system
     * @return the new {@link PathFile} instance
     */
    public static PathFile newSimpleTestFile(Object self, String path) {
        return FileTestingUtils.newSimpleTestFile(path);
    }
}
