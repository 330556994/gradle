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

package org.gradle.api.lazy;

import org.gradle.api.Buildable;
import org.gradle.api.Incubating;

/**
 * A {@code DerivedValue} represents a value of a specific type that is lazily evaluated.
 *
 * <p>
 * You can obtain a {@code DerivedValue} instance using {@link org.gradle.api.Project#derivedValue}.
 *
 * @param <T>
 * @since 3.5
 */
@Incubating
public interface DerivedValue<T> extends Buildable {

    /**
     * Evaluates lazy value and returns it.
     *
     * @return Value
     */
    T getValue();

    /**
     * Sets the tasks which build the files of this derived value.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    DerivedValue<T> builtBy(Object... tasks);
}
