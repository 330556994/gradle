/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class TaskEnumTypesInputPropertyIntegrationTest extends AbstractIntegrationSpec {
    @Issue("GRADLE-3018")
    def "task can take an input with enum type and task action defined in the build script"() {
        buildFile << """
task someTask {
    inputs.property "someEnum", SomeEnum.E1
    def f = file("build/e1")
    outputs.dir f
    doLast {
        f.mkdirs()
    }
}

enum SomeEnum {
    E1, E2
}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the build script
        when:
        buildFile << """
task someOtherTask
"""
        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the values of the enum
        when:
        editBuildFile("E1, E2", "E0, E1")

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "task can take an input with enum type and task type defined in the build script"() {
        buildFile << """
class SomeTask extends DefaultTask {
    @Input
    SomeEnum e
    @OutputDirectory
    File f
    @TaskAction
    def go() { }
}

task someTask(type: SomeTask) {
    e = SomeEnum.E1
    f = file("build/e1")
}

enum SomeEnum {
    E1, E2
}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the build script
        when:
        buildFile << """
task someOtherTask
"""
        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the values of the enum
        when:
        editBuildFile("E1, E2", "E0, E1")

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "task can take an input with enum type and task action defined in buildSrc"() {
        def enumSource = file("buildSrc/src/main/java/SomeEnum.java")
        enumSource << """
public enum SomeEnum {
    E1, E2
}
"""
        file("buildSrc/src/main/java/SomeTask.java") << """
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
public class SomeTask extends DefaultTask {
    @TaskAction
    public void go() { }
}
"""
        buildFile << """
task someTask(type: SomeTask) {
    inputs.property "someEnum", SomeEnum.E1
    def f = file("build/e1")
    outputs.dir f
}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the build script, should not affect task state
        when:
        buildFile << """
task someOtherTask
"""
        and:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the values of the enum
        when:
        enumSource.text = """
public enum SomeEnum {
    E0, E1
}
"""

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "task can take an input with enum type and task type defined in buildSrc"() {
        def enumSource = file("buildSrc/src/main/java/SomeEnum.java")
        enumSource << """
public enum SomeEnum {
    E1, E2
}
"""
        file("buildSrc/src/main/java/SomeTask.java") << """
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import java.io.File;

public class SomeTask extends DefaultTask {
    public SomeEnum e;
    @Input
    public SomeEnum getE() { return e; }

    public File f;
    @OutputDirectory
    public File getF() { return f; }
    
    @TaskAction
    public void go() { }
}
"""
        buildFile << """
task someTask(type: SomeTask) {
    e = SomeEnum.E1
    f = file("build/e1")
}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the build script, should not affect task state
        when:
        buildFile << """
task someOtherTask
"""
        and:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the values of the enum
        when:
        enumSource.text = """
public enum SomeEnum {
    E0, E1
}
"""

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }
}
