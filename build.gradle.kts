plugins {
	alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
	group = "com.sleepkqq"
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
		testLogging {
			events("passed", "skipped", "failed")
			showExceptions = true
			showCauses = true
			showStackTraces = true
			exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		}
	}

	configure<PublishingExtension> {
		publications {
			create<MavenPublication>("maven") {
				from(components["java"])

				artifactId = project.name

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
				}
			}
		}
	}
}
