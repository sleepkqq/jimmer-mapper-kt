plugins {
	alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
	group = "com.github.sleepkqq.jimmer-mapper-kt"
	version = findProperty("version") as String? ?: "0.0.0-SNAPSHOT"

	apply(plugin = "org.jetbrains.kotlin.jvm")
	apply(plugin = "maven-publish")

	repositories {
		mavenCentral()
	}

	configure<JavaPluginExtension> {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
		withSourcesJar()
	}

	tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
		compilerOptions {
			jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
		}
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}

	afterEvaluate {
		configure<PublishingExtension> {
			publications {
				create<MavenPublication>("maven") {
					artifactId = project.name

					artifact(tasks.named("jar"))
					artifact(tasks.named("sourcesJar"))

					pom {
						name.set(project.name)
						description.set("KSP processor for generating Jimmer entity mappers")
						url.set("https://github.com/sleepkqq/jimmer-mapper-kt")

						licenses {
							license {
								name.set("Apache License 2.0")
								url.set("https://www.apache.org/licenses/LICENSE-2.0")
							}
						}

						scm {
							url.set("https://github.com/sleepkqq/jimmer-mapper-kt")
						}

						withXml {
							val depsNode = asNode().appendNode("dependencies")
							configurations.getByName("implementation").allDependencies.forEach { dep ->
								depsNode.appendNode("dependency").apply {
									appendNode("groupId", if (dep is ProjectDependency) project.group.toString() else dep.group)
									appendNode("artifactId", dep.name)
									appendNode("version", if (dep is ProjectDependency) project.version.toString() else dep.version)
									appendNode("scope", "compile")
								}
							}
						}
					}
				}
			}
		}
	}
}
