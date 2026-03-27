package com.sleepkqq.jimmer.mapper.processor.model

enum class AssociationType {
	MANY_TO_ONE,
	ONE_TO_ONE,
	ONE_TO_MANY,
	MANY_TO_MANY,
}

data class EntityProperty(
	val name: String,
	val typeName: String,
	val isNullable: Boolean,
	val association: AssociationType?,
	val isId: Boolean,
	val isGeneratedValue: Boolean,
	val isVersion: Boolean,
	val isMappedSuperclassProperty: Boolean,
	val collectionElementTypeName: String? = null,
)
