/*
 * Copyright 2018 the original author or authors.
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
package org.beryx.jlink

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

class SuggestMergedModuleInfoSpec extends Specification {
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()

    static Set<String> GROOVY_DIRECTIVES = [
            "requires 'java.sql';",
            "requires 'java.naming';",
            "requires 'java.desktop';",
            "requires 'java.rmi';",
            "requires 'java.logging';",
            "requires 'java.compiler';",
            "requires 'java.scripting';",
            "requires 'java.xml';",
            "requires 'java.management';",
            "uses 'org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory';",
            "uses 'org.apache.logging.log4j.spi.Provider';",
            "provides 'javax.annotation.processing.Processor' with 'org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor';",
            "provides 'org.apache.logging.log4j.spi.Provider' with 'org.apache.logging.log4j.core.impl.Log4jProvider';",
            "provides 'org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory' with 'org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory';",
    ]

    static Set<String> KOTLIN_DIRECTIVES = [
            'requires("java.management");',
            'requires("java.naming");',
            'requires("java.logging");',
            'requires("java.scripting");',
            'requires("java.sql");',
            'requires("java.rmi");',
            'requires("java.xml");',
            'requires("java.desktop");',
            'requires("java.compiler");',
            'uses("org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory");',
            'uses("org.apache.logging.log4j.spi.Provider");',
            'provides("javax.annotation.processing.Processor").with("org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor");',
            'provides("org.apache.logging.log4j.spi.Provider").with("org.apache.logging.log4j.core.impl.Log4jProvider");',
            'provides("org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory").with("org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory");'
    ]
    static Set<String> JAVA_DIRECTIVES = [
            "requires java.sql;",
            "requires java.naming;",
            "requires java.desktop;",
            "requires java.rmi;",
            "requires java.logging;",
            "requires java.compiler;",
            "requires java.scripting;",
            "requires java.xml;",
            "requires java.management;",
            "uses org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory;",
            "uses org.apache.logging.log4j.spi.Provider;",
            "provides javax.annotation.processing.Processor with org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor;",
            "provides org.apache.logging.log4j.spi.Provider with org.apache.logging.log4j.core.impl.Log4jProvider;",
            "provides org.apache.logging.log4j.message.ThreadDumpMessage.ThreadInfoFactory with org.apache.logging.log4j.core.message.ExtendedThreadInfoFactory;",
    ]


    def cleanup() {
        println "CLEANUP"
    }

    @Unroll
    def "should display the correct module-info for the merged module with #language flavor using Gradle #gradleVersion"() {
        given:
        new AntBuilder().copy( todir: testProjectDir.root ) {
            fileset( dir: 'src/test/resources/hello-log4j-2.9.0' )
        }
        File buildFile = new File(testProjectDir.root, "build.gradle")
        def outputWriter = new StringWriter(8192)

        when:
        BuildResult result = GradleRunner.create()
                .withDebug(true)
                .withGradleVersion(gradleVersion)
                .forwardStdOutput(outputWriter)
                .withProjectDir(buildFile.parentFile)
                .withPluginClasspath()
                .withArguments("-is", JlinkPlugin.TASK_NAME_SUGGEST_MERGED_MODULE_INFO, '--useJdeps=no', "--language=$language")
                .build();
        def task = result.task(":$JlinkPlugin.TASK_NAME_SUGGEST_MERGED_MODULE_INFO")
        println outputWriter

        then:
        task.outcome == TaskOutcome.SUCCESS

        when:
        def taskOutput = outputWriter.toString()
        def directives = getDirectives(taskOutput, language)

        then:
        directives.size() == 14
        directives as Set == expectedDirectives

        where:
        language | expectedDirectives | gradleVersion
        'groovy' | GROOVY_DIRECTIVES  | '4.8'
        'kotlin' | KOTLIN_DIRECTIVES  | '4.10.3'
        'java'   | JAVA_DIRECTIVES    | '5.0'
    }

    List<String> getDirectives(String taskOutput, String language) {
        def blockStart = (language == 'kotlin') ? 'mergedModule (delegateClosureOf<ModuleInfo> {' : 'mergedModule {'
        def blockEnd = (language == 'kotlin') ? '})' : '}'

        int startPos = taskOutput.indexOf(blockStart)
        assert startPos >= 0
        startPos += blockStart.length()
        int endPos = taskOutput.indexOf(blockEnd, startPos)
        assert endPos >= 0
        def content = taskOutput.substring(startPos, endPos)
        content.lines().map{it.trim()}.filter{!it.empty}.collect(Collectors.toList())
    }
}
