package com.sleepkqq.jimmer.mapper.processor.model

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

data class MethodModel(
	val name: String,
	val returnEntityClassName: ClassName,
	val baseParam: BaseParam?,
	val sourceParams: List<SourceParam>,
	val mappings: List<PropertyMapping>,
	val returnType: TypeName,
)

data class BaseParam(
	val name: String,
	val typeName: TypeName,
)

data class SourceParam(
	val name: String,
	val typeName: TypeName,
	val properties: Map<String, TypeName>,
)
