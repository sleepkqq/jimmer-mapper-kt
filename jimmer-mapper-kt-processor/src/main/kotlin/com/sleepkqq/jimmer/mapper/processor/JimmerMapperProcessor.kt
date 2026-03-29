package com.sleepkqq.jimmer.mapper.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.validate
import com.sleepkqq.jimmer.mapper.annotation.Base
import com.sleepkqq.jimmer.mapper.annotation.IgnoreMapping
import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper
import com.sleepkqq.jimmer.mapper.annotation.Mapping
import com.sleepkqq.jimmer.mapper.processor.model.AssociationType
import com.sleepkqq.jimmer.mapper.processor.model.BaseParam
import com.sleepkqq.jimmer.mapper.processor.model.EntityProperty
import com.sleepkqq.jimmer.mapper.processor.model.MapperModel
import com.sleepkqq.jimmer.mapper.processor.model.MethodModel
import com.sleepkqq.jimmer.mapper.processor.model.SourceParam
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToMany
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.MappedSuperclass
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.OneToOne
import org.babyfish.jimmer.sql.Version

class JimmerMapperProcessor(
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {

	private companion object {
		val JIMMER_MAPPER_ANNOTATION = JimmerMapper::class.qualifiedName!!
		val BASE_ANNOTATION = Base::class.qualifiedName!!
		val MAPPING_ANNOTATION = Mapping::class.qualifiedName!!
		val IGNORE_MAPPING_ANNOTATION = IgnoreMapping::class.qualifiedName!!
		val JIMMER_ENTITY = Entity::class.qualifiedName!!
		val JIMMER_MAPPED_SUPERCLASS = MappedSuperclass::class.qualifiedName!!
		val JIMMER_ID = Id::class.qualifiedName!!
		val JIMMER_GENERATED_VALUE = GeneratedValue::class.qualifiedName!!
		val JIMMER_VERSION = Version::class.qualifiedName!!
		val JIMMER_MANY_TO_ONE = ManyToOne::class.qualifiedName!!
		val JIMMER_ONE_TO_ONE = OneToOne::class.qualifiedName!!
		val JIMMER_ONE_TO_MANY = OneToMany::class.qualifiedName!!
		val JIMMER_MANY_TO_MANY = ManyToMany::class.qualifiedName!!
	}

	private val mappingResolver = MappingResolver(logger)
	private val jimmerCodeGenerator = JimmerCodeGenerator(codeGenerator, options)
	private val generatedClasses = mutableListOf<String>()

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val symbols = resolver.getSymbolsWithAnnotation(JIMMER_MAPPER_ANNOTATION)
		val deferred = mutableListOf<KSAnnotated>()

		symbols.forEach { symbol ->
			if (!symbol.validate()) {
				deferred += symbol
				return@forEach
			}

			val classDecl = symbol as? KSClassDeclaration
			if (classDecl == null || classDecl.classKind != ClassKind.INTERFACE) {
				logger.error("@JimmerMapper can only be applied to interfaces", symbol)
				return@forEach
			}

			val model = buildMapperModel(classDecl)
			if (model != null) {
				jimmerCodeGenerator.generate(model)
				generatedClasses += model.implName.canonicalName
			}
		}

		return deferred
	}

	override fun finish() {
		if (generatedClasses.isEmpty()) return

		val nativeImageEnabled = options["jimmerMapper.nativeImage"]?.toBooleanStrictOrNull() == true
		if (nativeImageEnabled) {
			NativeImageConfigGenerator(codeGenerator).generate(generatedClasses)
		}
	}

	private fun buildMapperModel(classDecl: KSClassDeclaration): MapperModel? {
		val interfaceName = classDecl.toClassName()
		val implName = ClassName(interfaceName.packageName, "${interfaceName.simpleName}Impl")

		val abstractFunctions = classDecl.getDeclaredFunctions()
			.filter { it.isAbstract }
			.toList()

		// Build sibling method metadata for element mapper auto-discovery
		val siblingMethods = abstractFunctions.map { func ->
			val returnDecl = func.returnType?.resolve()?.declaration as? KSClassDeclaration
			val returnEntityType = if (returnDecl?.hasAnnotation(JIMMER_ENTITY) == true) {
				returnDecl.qualifiedName?.asString()
			} else null

			MappingResolver.SiblingMethod(
				name = func.simpleName.asString(),
				parameterTypes = func.parameters.map {
					it.type.resolve().declaration.qualifiedName?.asString() ?: ""
				},
				returnEntityType = returnEntityType,
			)
		}

		val methods = abstractFunctions.mapNotNull { func ->
			val funcParamTypes = func.parameters.map {
				it.type.resolve().declaration.qualifiedName?.asString() ?: ""
			}
			buildMethodModel(func, classDecl, siblingMethods.filter { sibling ->
				sibling.name != func.simpleName.asString() || sibling.parameterTypes != funcParamTypes
			})
		}

		if (methods.isEmpty()) {
			logger.warn("@JimmerMapper interface ${classDecl.simpleName.asString()} has no abstract methods", classDecl)
			return null
		}

		return MapperModel(
			interfaceName = interfaceName,
			implName = implName,
			methods = methods,
			originatingFile = classDecl.containingFile,
		)
	}

	private fun buildMethodModel(
		func: KSFunctionDeclaration,
		classDecl: KSClassDeclaration,
		siblingMethods: List<MappingResolver.SiblingMethod>,
	): MethodModel? {
		val returnType = func.returnType?.resolve() ?: run {
			logger.error("Method ${func.simpleName.asString()} must have a return type", func)
			return null
		}

		val returnDecl = returnType.declaration as? KSClassDeclaration ?: run {
			logger.error("Return type must be a class/interface", func)
			return null
		}

		if (!returnDecl.hasAnnotation(JIMMER_ENTITY)) {
			logger.error("Return type ${returnDecl.simpleName.asString()} must be a Jimmer @Entity", func)
			return null
		}

		val entityProperties = collectEntityProperties(returnDecl)
		val baseParam = findBaseParam(func)
		val sourceParams = buildSourceParams(func)

		val explicitMappings = parseExplicitMappings(func)
		val ignoredProperties = parseIgnoredProperties(func)

		// Build a resolver for entity property types (for nested entity creation)
		val entityPropertyTypeResolver: (String) -> ClassName? = { propName ->
			val prop = returnDecl.getAllProperties().find { it.simpleName.asString() == propName }
			val propTypeDecl = prop?.type?.resolve()?.declaration as? KSClassDeclaration
			if (propTypeDecl?.hasAnnotation(JIMMER_ENTITY) == true) {
				propTypeDecl.toClassName()
			} else null
		}

		val mappings = mappingResolver.resolve(
			MappingResolver.ResolveContext(
				entityProperties = entityProperties,
				baseParam = baseParam,
				sourceParams = sourceParams,
				explicitMappings = explicitMappings,
				ignoredProperties = ignoredProperties,
				funcNode = func,
				entityPropertyTypeResolver = entityPropertyTypeResolver,
				siblingMethods = siblingMethods,
			),
		)

		return MethodModel(
			name = func.simpleName.asString(),
			returnEntityClassName = returnDecl.toClassName(),
			baseParam = baseParam,
			sourceParams = sourceParams,
			mappings = mappings,
			returnType = returnType.toTypeName(),
		)
	}

	private fun collectEntityProperties(classDecl: KSClassDeclaration): List<EntityProperty> {
		val properties = mutableListOf<EntityProperty>()

		fun collectFrom(decl: KSClassDeclaration, isMappedSuperclass: Boolean) {
			decl.getAllProperties().forEach { prop ->
				val fromMappedSuperclass = isMappedSuperclass ||
					(prop.parentDeclaration as? KSClassDeclaration)?.hasAnnotation(JIMMER_MAPPED_SUPERCLASS) == true

				val propType = prop.type.resolve()
				val elementTypeName = propType.arguments.firstOrNull()
					?.type?.resolve()?.declaration?.qualifiedName?.asString()

				properties += EntityProperty(
					name = prop.simpleName.asString(),
					typeName = propType.declaration.qualifiedName?.asString() ?: "",
					isNullable = propType.isMarkedNullable,
					association = resolveAssociation(prop),
					isId = prop.hasAnnotation(JIMMER_ID),
					isGeneratedValue = prop.hasAnnotation(JIMMER_GENERATED_VALUE),
					isVersion = prop.hasAnnotation(JIMMER_VERSION),
					isMappedSuperclassProperty = fromMappedSuperclass,
					collectionElementTypeName = elementTypeName,
				)
			}
		}

		collectFrom(classDecl, classDecl.hasAnnotation(JIMMER_MAPPED_SUPERCLASS))
		return properties.distinctBy { it.name }
	}

	private fun resolveAssociation(prop: KSPropertyDeclaration): AssociationType? = when {
		prop.hasAnnotation(JIMMER_MANY_TO_ONE) -> AssociationType.MANY_TO_ONE
		prop.hasAnnotation(JIMMER_ONE_TO_ONE) -> AssociationType.ONE_TO_ONE
		prop.hasAnnotation(JIMMER_ONE_TO_MANY) -> AssociationType.ONE_TO_MANY
		prop.hasAnnotation(JIMMER_MANY_TO_MANY) -> AssociationType.MANY_TO_MANY
		else -> null
	}

	private fun findBaseParam(func: KSFunctionDeclaration): BaseParam? {
		val baseParams = func.parameters.filter { it.hasAnnotation(BASE_ANNOTATION) }
		if (baseParams.size > 1) {
			logger.error("Only one @Base parameter is allowed", func)
			return null
		}
		return baseParams.firstOrNull()?.let {
			BaseParam(
				name = it.name?.asString() ?: "base",
				typeName = it.type.toTypeName(),
			)
		}
	}

	private fun buildSourceParams(func: KSFunctionDeclaration): List<SourceParam> =
		func.parameters
			.filter { !it.hasAnnotation(BASE_ANNOTATION) }
			.map { param ->
				val paramType = param.type.resolve()
				val paramDecl = paramType.declaration as? KSClassDeclaration
				val props = paramDecl?.getAllProperties()
					?.associate { it.simpleName.asString() to it.type.toTypeName() }
					?: emptyMap()

				SourceParam(
					name = param.name?.asString() ?: "p",
					typeName = param.type.toTypeName(),
					properties = props,
				)
			}

	private fun parseExplicitMappings(func: KSFunctionDeclaration): List<Pair<String, String>> =
		func.annotations
			.filter { it.annotationType.resolve().declaration.qualifiedName?.asString() == MAPPING_ANNOTATION }
			.map { annotation ->
				val args = annotation.arguments.associate {
					it.name?.asString() to it.value?.toString()
				}
				(args["source"] ?: "") to (args["target"] ?: "")
			}
			.toList()

	private fun parseIgnoredProperties(func: KSFunctionDeclaration): Set<String> {
		val ignoreAnnotation = func.annotations
			.find { it.annotationType.resolve().declaration.qualifiedName?.asString() == IGNORE_MAPPING_ANNOTATION }
			?: return emptySet()

		val value = ignoreAnnotation.arguments.firstOrNull()?.value
		return when (value) {
			is List<*> -> value.filterIsInstance<String>().toSet()
			is Array<*> -> value.filterIsInstance<String>().toSet()
			else -> emptySet()
		}
	}

	private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean =
		annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }

	private fun KSValueParameter.hasAnnotation(qualifiedName: String): Boolean =
		annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }
}
