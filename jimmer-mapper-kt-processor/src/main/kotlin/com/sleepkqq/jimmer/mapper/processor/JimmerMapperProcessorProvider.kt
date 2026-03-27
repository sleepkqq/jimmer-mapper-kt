package com.sleepkqq.jimmer.mapper.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class JimmerMapperProcessorProvider : SymbolProcessorProvider {

	override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
		JimmerMapperProcessor(
			codeGenerator = environment.codeGenerator,
			logger = environment.logger,
			options = environment.options,
		)
}
