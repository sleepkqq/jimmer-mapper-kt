package com.sleepkqq.jimmer.mapper.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.sleepkqq.jimmer.mapper.processor.model.MapperModel
import com.sleepkqq.jimmer.mapper.processor.model.MethodModel
import com.sleepkqq.jimmer.mapper.processor.model.PropertyMapping
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton

class JimmerCodeGenerator(
	private val codeGenerator: CodeGenerator,
	private val options: Map<String, String>,
) {

	fun generate(model: MapperModel) {
		val cdiAnnotation = resolveCdiAnnotation()

		val classBuilder = TypeSpec.classBuilder(model.implName)
			.addSuperinterface(model.interfaceName)

		if (cdiAnnotation != null) {
			classBuilder.addAnnotation(AnnotationSpec.builder(cdiAnnotation).build())
		}

		model.methods.forEach { method ->
			classBuilder.addFunction(generateMethod(method))
		}

		val fileSpec = FileSpec.builder(model.implName)
			.addType(classBuilder.build())
			.build()

		val deps = if (model.originatingFile != null) {
			Dependencies(aggregating = false, model.originatingFile!!)
		} else {
			Dependencies(aggregating = false)
		}

		fileSpec.writeTo(codeGenerator, deps)
	}

	private fun generateMethod(method: MethodModel): FunSpec {
		val funBuilder = FunSpec.builder(method.name)
			.addModifiers(KModifier.OVERRIDE)
			.returns(method.returnType)

		method.baseParam?.let { base ->
			funBuilder.addParameter(base.name, base.typeName)
		}

		method.sourceParams.forEach { param ->
			funBuilder.addParameter(param.name, param.typeName)
		}

		funBuilder.addCode(generateBody(method))
		return funBuilder.build()
	}

	private fun generateBody(method: MethodModel): CodeBlock {
		val builder = CodeBlock.builder()

		if (method.baseParam != null) {
			builder.beginControlFlow("return %T(%N)", method.returnEntityClassName, method.baseParam.name)
		} else {
			builder.beginControlFlow("return %T", method.returnEntityClassName)
		}

		method.mappings.forEach { mapping ->
			when (mapping) {
				is PropertyMapping.Direct -> {
					val prefix = if (mapping.targetProperty == mapping.sourceExpression) "this." else ""
					builder.addStatement("${prefix}%N = %L", mapping.targetProperty, mapping.sourceExpression)
				}

				is PropertyMapping.ForeignKey ->
					builder.addStatement("this.%N = %L", mapping.fkIdProperty, mapping.paramName)

				is PropertyMapping.CollectionMerge ->
					builder.addStatement("this.%N = %L + %L", mapping.targetProperty, mapping.baseExpression, mapping.addExpression)

				is PropertyMapping.NestedEntity ->
					generateNestedEntity(builder, mapping)

				is PropertyMapping.CollectionElementMapping ->
					builder.addStatement(
						"%N = %L.map { %N(it) }",
						mapping.targetProperty,
						mapping.sourceExpression,
						mapping.elementMapperMethod,
					)
			}
		}

		builder.endControlFlow()
		return builder.build()
	}

	private fun generateNestedEntity(builder: CodeBlock.Builder, mapping: PropertyMapping.NestedEntity) {
		builder.beginControlFlow("%N = %T", mapping.targetProperty, mapping.entityClassName)
		mapping.nestedMappings.forEach { (source, target) ->
			builder.addStatement("%N = %L", target, source)
		}
		builder.endControlFlow()
	}

	private fun resolveCdiAnnotation(): ClassName? {
		val option = options["jimmerMapper.cdiAnnotation"] ?: "applicationScoped"
		return when (option) {
			"applicationScoped" -> ApplicationScoped::class.asClassName()
			"singleton" -> Singleton::class.asClassName()
			"none" -> null
			else -> ApplicationScoped::class.asClassName()
		}
	}
}
