package com.sleepkqq.jimmer.mapper.processor.model

import com.squareup.kotlinpoet.ClassName

sealed class PropertyMapping {
	abstract val targetProperty: String

	data class Direct(
		override val targetProperty: String,
		val sourceExpression: String,
	) : PropertyMapping()

	data class ForeignKey(
		override val targetProperty: String,
		val fkIdProperty: String,
		val paramName: String,
	) : PropertyMapping()

	data class CollectionMerge(
		override val targetProperty: String,
		val baseExpression: String,
		val addExpression: String,
	) : PropertyMapping()

	data class NestedEntity(
		override val targetProperty: String,
		val entityClassName: ClassName,
		val nestedMappings: List<Pair<String, String>>,
	) : PropertyMapping()

	data class CollectionElementMapping(
		override val targetProperty: String,
		val sourceExpression: String,
		val elementMapperMethod: String,
	) : PropertyMapping()
}
