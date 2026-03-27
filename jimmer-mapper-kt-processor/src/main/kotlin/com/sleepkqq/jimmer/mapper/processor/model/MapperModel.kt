package com.sleepkqq.jimmer.mapper.processor.model

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName

data class MapperModel(
	val interfaceName: ClassName,
	val implName: ClassName,
	val methods: List<MethodModel>,
	val originatingFile: KSFile?,
)
