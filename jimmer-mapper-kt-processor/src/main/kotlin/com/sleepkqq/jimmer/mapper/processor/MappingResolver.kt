package com.sleepkqq.jimmer.mapper.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import com.sleepkqq.jimmer.mapper.processor.model.AssociationType
import com.sleepkqq.jimmer.mapper.processor.model.BaseParam
import com.sleepkqq.jimmer.mapper.processor.model.EntityProperty
import com.sleepkqq.jimmer.mapper.processor.model.PropertyMapping
import com.sleepkqq.jimmer.mapper.processor.model.SourceParam
import com.squareup.kotlinpoet.ClassName

class MappingResolver(private val logger: KSPLogger) {

	data class ResolveContext(
		val entityProperties: List<EntityProperty>,
		val baseParam: BaseParam?,
		val sourceParams: List<SourceParam>,
		val explicitMappings: List<Pair<String, String>>,
		val ignoredProperties: Set<String>,
		val funcNode: KSNode,
		val entityPropertyTypeResolver: (String) -> ClassName?,
		val siblingMethods: List<SiblingMethod>,
	)

	data class SiblingMethod(
		val name: String,
		val parameterTypes: List<String>,
		val returnEntityType: String?,
	)

	fun resolve(context: ResolveContext): List<PropertyMapping> {
		val result = mutableListOf<PropertyMapping>()

		// 1. Group dotted-path explicit mappings into NestedEntity mappings
		val (dottedMappings, flatMappings) = context.explicitMappings.partition { it.second.contains(".") }
		val nestedGroups = dottedMappings.groupBy { it.second.substringBefore(".") }

		for ((targetProp, mappings) in nestedGroups) {
			val entityClassName = context.entityPropertyTypeResolver(targetProp)
			if (entityClassName == null) {
				logger.error("Cannot resolve entity type for nested property '$targetProp'", context.funcNode)
				continue
			}
			result += PropertyMapping.NestedEntity(
				targetProperty = targetProp,
				entityClassName = entityClassName,
				nestedMappings = mappings.map { it.first to it.second.substringAfter(".") },
			)
		}

		// 2. Collect properties already handled by nested mappings
		val nestedHandled = nestedGroups.keys

		for (prop in context.entityProperties) {
			if (prop.name in nestedHandled) continue

			val mapping = resolveProperty(
				prop = prop,
				context = context,
				flatMappings = flatMappings,
			)
			if (mapping != null) {
				result += mapping
			}
		}

		return result
	}

	private fun resolveProperty(
		prop: EntityProperty,
		context: ResolveContext,
		flatMappings: List<Pair<String, String>>,
	): PropertyMapping? {
		// 1. Skip ignored
		if (prop.name in context.ignoredProperties) return null

		// 2. Skip auto-generated ID
		if (prop.isId && prop.isGeneratedValue) return null

			// 3. Skip @OneToMany / @ManyToMany by default (unless explicitly mapped)
		if (prop.association == AssociationType.ONE_TO_MANY || prop.association == AssociationType.MANY_TO_MANY) {
			val explicitForProp = flatMappings.find { it.second == prop.name }
			if (explicitForProp != null) {
				return resolveCollectionMapping(prop, explicitForProp.first, context)
			}

			// Check for collection merge with @Base
			if (context.baseParam != null) {
				val matchingParam = context.sourceParams.find { param ->
					param.name == prop.name || param.name == prop.name + "s"
				}
				if (matchingParam != null) {
					val addExpression = resolveCollectionMergeExpression(
						param = matchingParam,
						prop = prop,
						context = context,
					)
					return PropertyMapping.CollectionMerge(
						targetProperty = prop.name,
						baseExpression = "${context.baseParam.name}.${prop.name}",
						addExpression = addExpression,
					)
				}
			}
			return null
		}

		// 4. Skip MappedSuperclass properties (version, createdAt, updatedAt)
		if (prop.isMappedSuperclassProperty && !prop.isId) return null

		// 5. Explicit @Mapping (flat only)
		val explicit = flatMappings.find { it.second == prop.name }
		if (explicit != null) {
			return PropertyMapping.Direct(
				targetProperty = prop.name,
				sourceExpression = explicit.first,
			)
		}

		// 6. FK pattern: paramName = "{entityProp}Id" + target is @ManyToOne/@OneToOne
		if (prop.association == AssociationType.MANY_TO_ONE || prop.association == AssociationType.ONE_TO_ONE) {
			val fkParamName = "${prop.name}Id"
			val fkParam = context.sourceParams.find { it.name == fkParamName }
			if (fkParam != null) {
				return PropertyMapping.ForeignKey(
					targetProperty = prop.name,
					fkIdProperty = fkParamName,
					paramName = fkParamName,
				)
			}
			for (param in context.sourceParams) {
				if (fkParamName in param.properties) {
					return PropertyMapping.ForeignKey(
						targetProperty = prop.name,
						fkIdProperty = fkParamName,
						paramName = "${param.name}.$fkParamName",
					)
				}
			}
		}

		// 7. Direct name match
		val directParam = context.sourceParams.find { it.name == prop.name }
		if (directParam != null) {
			return PropertyMapping.Direct(
				targetProperty = prop.name,
				sourceExpression = directParam.name,
			)
		}
		for (param in context.sourceParams) {
			if (prop.name in param.properties) {
				return PropertyMapping.Direct(
					targetProperty = prop.name,
					sourceExpression = "${param.name}.${prop.name}",
				)
			}
		}

		// 8. @ManyToOne/@OneToOne without FK match — skipped (Jimmer binds on save)
		if (prop.association == AssociationType.MANY_TO_ONE || prop.association == AssociationType.ONE_TO_ONE) {
			return null
		}

		// 9. @Base inheritance — properties carry over automatically
		if (context.baseParam != null) return null

		// 10. Nullable — defaults to null
		if (prop.isNullable) return null

		// 11. Non-nullable @Id without @GeneratedValue — must be provided
		if (prop.isId) {
			val idParam = context.sourceParams.find { it.name == "id" }
				?: context.sourceParams.flatMap { p -> p.properties.keys.map { p to it } }
					.find { it.second == "id" }
					?.let { context.sourceParams.find { s -> s.name == it.first.name } }

			if (idParam != null) {
				val hasIdProp = "id" in idParam.properties
				return PropertyMapping.Direct(
					targetProperty = "id",
					sourceExpression = if (hasIdProp) "${idParam.name}.id" else idParam.name,
				)
			}
		}

		// Error: required property without match
		logger.error(
			"Cannot resolve mapping for required property '${prop.name}' of type '${prop.typeName}'",
			context.funcNode,
		)
		return null
	}

	private fun resolveCollectionMergeExpression(
		param: SourceParam,
		prop: EntityProperty,
		context: ResolveContext,
	): String {
		val elementType = prop.collectionElementTypeName ?: return param.name

		// Check if param is already the target type (List<Subway> -> List<Subway>)
		val paramTypeStr = param.typeName.toString()
		if (paramTypeStr.contains(elementType)) return param.name

		// Look for sibling method that returns the element entity type
		val elementMapper = context.siblingMethods.find { it.returnEntityType == elementType }
			?: return param.name

		return "${param.name}.map { ${elementMapper.name}(it) }"
	}

	private fun resolveCollectionMapping(
		prop: EntityProperty,
		sourceExpression: String,
		context: ResolveContext,
	): PropertyMapping {
		// Try to find a sibling method that maps element type
		val elementType = prop.collectionElementTypeName ?: prop.typeName
		val elementMapper = context.siblingMethods.find { method ->
			method.returnEntityType == elementType
		}

		return if (elementMapper != null) {
			PropertyMapping.CollectionElementMapping(
				targetProperty = prop.name,
				sourceExpression = sourceExpression,
				elementMapperMethod = elementMapper.name,
			)
		} else {
			PropertyMapping.Direct(
				targetProperty = prop.name,
				sourceExpression = sourceExpression,
			)
		}
	}
}
