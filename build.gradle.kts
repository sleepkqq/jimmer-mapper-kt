plugins {
	alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
	group = "com.github.sleepkqq.jimmer-mapper-kt"
	version = "1.0.0"

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

	configure<PublishingExtension> {
		publications {
			create<MavenPublication>("maven") {
				from(components["java"])

				groupId = "com.github.sleepkqq.jimmer-mapper-kt"
				artifactId = project.name

				pom {
					name.set(project.name)
					description.set("KSP processor for generating Jimmer entity mappers")
					url.set("https://github.com/sleepkqq/jimmer-mapper-kt")

					licenses {
						license {
							name.set("MIT License")
							url.set("https://opensource.org/licenses/MIT")
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
