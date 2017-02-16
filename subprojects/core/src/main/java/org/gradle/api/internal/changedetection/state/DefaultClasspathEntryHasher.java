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

package org.gradle.api.internal.changedetection.state;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DefaultClasspathEntryHasher implements ClasspathEntryHasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(DefaultClasspathEntryHasher.class.getName(), Charsets.UTF_8).asBytes();
    private final ClasspathContentHasher classpathContentHasher;

    public DefaultClasspathEntryHasher(ClasspathContentHasher classpathContentHasher) {
        this.classpathContentHasher = classpathContentHasher;
    }

    @Override
    public HashCode hash(FileDetails fileDetails) {
        if (fileDetails.getType() == FileType.Directory || fileDetails.getType() == FileType.Missing) {
            return null;
        }

        String name = fileDetails.getName();
        final Hasher hasher = createHasher();
        if (FileUtils.isJar(name)) {
            return hashJar(fileDetails, hasher, classpathContentHasher);
        } else {
            return hashFile(fileDetails, hasher, classpathContentHasher);
        }
    }

    private Hasher createHasher() {
        Hasher hasher = Hashing.md5().newHasher();
        hasher.putBytes(SIGNATURE);
        return hasher;
    }

    private HashCode hashJar(FileDetails fileDetails, Hasher hasher, ClasspathContentHasher classpathContentHasher) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(new File(fileDetails.getPath()));
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            // Ensure we visit the zip entries in a deterministic order
            Map<String, ZipEntry> entriesByName = new TreeMap<String, ZipEntry>();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    entriesByName.put(zipEntry.getName(), zipEntry);
                }
            }
            for (ZipEntry zipEntry : entriesByName.values()) {
                visit(zipFile, zipEntry, hasher, classpathContentHasher);
            }
            return hasher.hash();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(zipFile);
        }
    }

    private HashCode hashFile(FileDetails fileDetails, Hasher hasher, ClasspathContentHasher classpathContentHasher) {
        try {
            byte[] content = Files.toByteArray(new File(fileDetails.getPath()));
            if (classpathContentHasher.updateHash(fileDetails, hasher, content)) {
                return hasher.hash();
            }
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void visit(ZipFile zipFile, ZipEntry zipEntry, Hasher hasher, ClasspathContentHasher classpathContentHasher) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = zipFile.getInputStream(zipEntry);
            byte[] src = ByteStreams.toByteArray(inputStream);
            classpathContentHasher.updateHash(zipFile, zipEntry, hasher, src);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }
}
