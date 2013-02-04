package org.primeframework.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The database plugin is used for creating database
 *
 * @author James Humphrey
 */
class TestngPlugin implements Plugin<Project> {

  def void apply(Project project) {
    project.extensions.add("testng", new TestngPluginConfiguration())

    project.task("test", overwrite: true, dependsOn: ["jar", "testClasses"]) << {
      ant.taskdef(name: 'testng', classname: 'org.testng.TestNGAntTask', classpath: project.sourceSets.test.runtimeClasspath.asPath)

      println "Executing testng tests..."

      String skip = project.gradle.startParameter.mergedSystemProperties['skipTests']
      if (skip == 'true') {
        println "Skipping testing!"
      } else {
        def dbGroups = ["integration", "acceptance"]
        def group = project.gradle.startParameter.mergedSystemProperties['group']
        def dbTypes = []
        try {
          dbTypes = project.database.types
        } catch (Exception e) {
          // do nothing, dbTypes will just be null
        }

        if (group == null) {
          runTests(project, null, "unit")
          if (dbTypes.size() > 0) {
            dbTypes.each { dbType ->
              dbGroups.each { thisGroup ->
                runTests(project, dbType, thisGroup)
              }
            }
          } else {
            dbGroups.each { thisGroup ->
              runTests(project, null, thisGroup)
            }
          }
        } else {
          if (group == "unit" || group == "performance") {
            runTests(project, null, group)
          } else {
            if (dbTypes.size() > 0) {
              dbTypes.each { dbType ->
                runTests(project, dbType, group)
              }
            } else {
              dbGroups.each { thisGroup ->
                runTests(project, null, thisGroup)
              }
            }
          }
        }
      }
    }
  }

  private void runTests(Project project, def dbType, def group) {
    if (dbType != null) {
      println "Executing [${group}] tests against database [$dbType]"
    } else {
      println "Executing [${group}] tests"
    }

    def singleTest = project.gradle.startParameter.mergedSystemProperties['test']
    def includes = "**/*"
    if (singleTest != null) {
      println "Testing single test [${singleTest}]"
      includes = "**/${singleTest}.class"
    }

    def databaseType = null
    if (dbType != null) {
      databaseType = "-Ddatabase.type=$dbType";
    }

    def outputDir = "${project.buildDir}/reports/tests"
    if (dbType == null) {
      outputDir += "/${group}"
    } else {
      outputDir += "/${group}/${dbType}/"
    }

    project.ant.testng(enableAssert: true, outputDir: outputDir, haltOnFailure: true, threadCount: 1, groups: group) {
      jvmarg(value: "-Xmx${project.testng.maxMemory}")
      jvmarg(value: "-Djava.util.logging.config.file=src/test/resources/logging.properties")
      jvmarg(value: "-Dfile.encoding=UTF8")

      if (databaseType != null) {
        jvmarg(value: databaseType)
      }

      jvmarg(line: "-DlogFilePath=${project.testng.logFile}")
      jvmarg(line: "${project.testng.jvmArgs}")
      classpath {
        fileset(dir: "${project.buildDir}/libs") {
          include(name: "**/*.jar")
        }
        pathElement(path: project.sourceSets.test.runtimeClasspath.asPath)
        path(location: "${project.buildDir}/classes/test")
        path(location: "${project.buildDir}/resources/test")
      }
      classfileset(dir: "${project.buildDir}/classes/test", includes: includes)
    }
  }

  /**
   * Configuration bean
   */
  class TestngPluginConfiguration {
    def maxMemory = "256M"
    def jvmArgs = ""
    def logFile = "build/testng.out"
  }
}
