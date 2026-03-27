plugins {
	alias(libs.plugins.kotlin.jvm)
}

dependencies {
	implementation(project(":jimmer-mapper-kt-annotations"))
	implementation(libs.ksp.api)
	implementation(libs.kotlinpoet)
	implementation(libs.kotlinpoet.ksp)
	implementation(libs.jimmer.sql.kotlin)
	implementation(libs.jakarta.inject.api)
	implementation(libs.jakarta.cdi.api)

	testImplementation(libs.compile.testing.ksp)
	testImplementation(libs.jimmer.sql.kotlin)
	testImplementation(libs.jimmer.ksp)
	testImplementation(libs.junit.api)
	testRuntimeOnly(libs.junit.engine)
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
