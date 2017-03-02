/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.caching.internal;

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.util.GFileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DispatchingBuildCacheService implements BuildCacheService {
    private final BuildCacheService local;
    private final boolean pushToLocal;
    private final BuildCacheService remote;
    private final boolean pushToRemote;
    private final TemporaryFileProvider temporaryFileProvider;

    DispatchingBuildCacheService(TemporaryFileProvider temporaryFileProvider, BuildCacheService local, boolean pushToLocal, BuildCacheService remote, boolean pushToRemote) {
        this.local = local;
        this.pushToLocal = pushToLocal;
        this.remote = remote;
        this.pushToRemote = pushToRemote;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        return local.load(key, reader) || remote.load(key, reader);
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        if (pushToLocal && pushToRemote) {
            File destination = temporaryFileProvider.createTemporaryFile("gradle_cache", "entry");
            try {
                writeCacheEntryLocally(writer, destination);
                BuildCacheEntryWriter copier = new CopyBuildCacheEntryWriter(destination);
                local.store(key, copier);
                remote.store(key, copier);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                GFileUtils.deleteQuietly(destination);
            }
        } else if (pushToLocal) {
            local.store(key, writer);
        } else if (pushToRemote) {
            remote.store(key, writer);
        }
    }

    private void writeCacheEntryLocally(BuildCacheEntryWriter writer, File destination) throws IOException {
        OutputStream fileOutputStream = null;
        try {
            fileOutputStream = new BufferedOutputStream(new FileOutputStream(destination));
            writer.writeTo(fileOutputStream);
        } catch (FileNotFoundException e) {
            throw new BuildCacheException("Couldn't create local file for cache entry", e);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    @Override
    public String getDescription() {
        return decorateDescription(pushToLocal, local.getDescription()) + " and " + decorateDescription(pushToRemote, remote.getDescription());
    }

    private String decorateDescription(boolean pushTo, String description) {
        if (pushTo) {
            return description + " (pushing enabled)";
        }
        return description;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(local, remote).stop();
    }

    /**
     * Writes a local file into a build cache
     */
    private static class CopyBuildCacheEntryWriter implements BuildCacheEntryWriter {
        private final File destination;

        private CopyBuildCacheEntryWriter(File destination) {
            this.destination = destination;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            InputStream fileInputStream = null;
            try {
                fileInputStream = new BufferedInputStream(new FileInputStream(destination));
                IOUtils.copyLarge(fileInputStream, output);
            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        }
    }
}
