package com.github.sybrininnovations.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin



class SybrinPublishConfigPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def extension = project.extensions.create('publishInfo', SybrinPublishExtension)

        project.afterEvaluate {
            println "*******************Publish info***********************"
            println "Publishing url: ${extension.publishURL}"
            println "Group name: ${project.group}"
            println "Artifact version: ${project.version}"
            println "Artifact ID: ${project.name}"
            println "******************************************************"

            project.getPluginManager().apply(MavenPublishPlugin.class);
            project.getExtensions().configure(PublishingExtension.class, publishing -> {

                publishing.repositories {
                    maven {
                        name = 'GitHubPackages'
                        url = project.uri(extension.publishURL)
                        credentials {
                            username extension.userName
                            password extension.password
                        }
                    }
                }

                publishing.publications(publications -> {
                    maven(MavenPublication) {

                        groupId project.group
                        artifactId project.name
                        version project.version

                        versionMapping {
                            usage('java-api') {
                                fromResolutionOf('runtimeClasspath')
                            }
                            usage('java-runtime') {
                                fromResolutionResult()
                            }
                        }

                        //aar artifact you want to publish
                        artifact "./build/outputs/aar/${project.name}-release.aar"


                        pom.withXml() {
                            final dependenciesNode = asNode().appendNode('dependencies')

                            ext.addDependency = { Dependency dep, String scope ->
                                if (dep.group == null || dep.version == null || dep.name == null || dep.name == "unspecified")
                                    return // invalid dependencies should be ignored

                                final dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('artifactId', dep.name)

                                if (dep.version == 'unspecified') {
                                    //If it goes in here you probably didn't specify a group and version in the consuming gradle file
                                } else {
                                    dependencyNode.appendNode('groupId', dep.group)
                                    dependencyNode.appendNode('version', dep.version)
                                }

                                dependencyNode.appendNode('scope', scope)
                                // Some dependencies may have types, such as aar, that should be mentioned in the POM file
                                def artifactsList = dep.properties['artifacts']
                                if (artifactsList != null && artifactsList.size() > 0) {
                                    final artifact = artifactsList[0]
                                    dependencyNode.appendNode('type', artifact.getType())
                                }

                                if (!dep.transitive) {
                                    // In case of non transitive dependency, all its dependencies should be force excluded from them POM file
                                    final exclusionNode = dependencyNode.appendNode('exclusions').appendNode('exclusion')
                                    exclusionNode.appendNode('groupId', '*')
                                    exclusionNode.appendNode('artifactId', '*')
                                } else if (!dep.properties.excludeRules.empty) {
                                    // For transitive with exclusions, all exclude rules should be added to the POM file
                                    final exclusions = dependencyNode.appendNode('exclusions')
                                    dep.properties.excludeRules.each { ExcludeRule rule ->
                                        final exclusionNode = exclusions.appendNode('exclusion')
                                        exclusionNode.appendNode('groupId', rule.group ?: '*')
                                        exclusionNode.appendNode('artifactId', rule.module ?: '*')
                                    }
                                }
                            }

                            project.configurations.api.getDependencies().each { dep -> addDependency(dep, "compile") }
                            project.configurations.implementation.getDependencies().each { dep -> addDependency(dep, "runtime") }
                        }
                    }
                });
            });
        }
    }
}