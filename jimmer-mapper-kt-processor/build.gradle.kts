plugins {
	alias(libs.plugins.kotlin.jvm)
}

dependencies {
	implementation(project(":jimmer-mapper-kt-annotations"))
	implementation(libs.ksp.api)
	implementation(libs.kotlinpoet)
	implementation(libs.kotlinpoet.ksp)
	compileOnly(libs.jimmer.sql.kotlin)
	compileOnly(libs.jakarta.inject.api)
	compileOnly(libs.jakarta.cdi.api)
	compileOnly(libs.spring.context)

	testImplementation(libs.compile.testing.ksp)
	testImplementation(libs.jimmer.sql.kotlin)
	testImplementation(libs.jimmer.ksp)
	testImplementation(libs.junit.api)
	testRuntimeOnly(libs.junit.engine)
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
