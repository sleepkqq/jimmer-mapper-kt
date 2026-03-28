package com.sleepkqq.jimmer.mapper.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies

class NativeImageConfigGenerator(
	private val codeGenerator: CodeGenerator,
) {

	private companion object {
		const val CONFIG_PATH = "META-INF/native-image/com.sleepkqq/jimmer-mapper-kt"
		const val REFLECT_CONFIG = "reflect-config.json"
	}

	fun generate(classNames: List<String>) {
		val entries = classNames.joinToString(",\n") { className ->
			"""  {
    "name": "$className",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  }"""
		}

		val json = "[\n$entries\n]\n"

		codeGenerator.createNewFile(
			dependencies = Dependencies(aggregating = true),
			packageName = "",
			fileName = "$CONFIG_PATH/$REFLECT_CONFIG",
			extensionName = "",
		).use { stream ->
			stream.write(json.toByteArray())
		}
	}
}
