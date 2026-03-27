package com.sleepkqq.jimmer.mapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class JimmerMapperProcessorTest {

	private data class CompileResult(
		val exitCode: KotlinCompilation.ExitCode,
		val messages: String,
		val generatedSources: Map<String, String>,
	)

	private fun source(name: String): SourceFile {
		val content = javaClass.getResource("/sources/$name")?.readText()
			?: error("Test source not found: /sources/$name")
		return SourceFile.kotlin(name, content)
	}

	private fun compile(vararg sourceNames: String): CompileResult {
		val sources = sourceNames.map { source(it) }
		val compilation = KotlinCompilation().apply {
			this.sources = sources
			inheritClassPath = true
			configureKsp(useKsp2 = true) {
				symbolProcessorProviders += JimmerMapperProcessorProvider()
				processorOptions += "jimmerMapper.cdiAnnotation" to "none"
			}
		}

		val result = compilation.compile()

		val generated = compilation.kspSourcesDir.walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.associate { it.name to it.readText() }

		return CompileResult(result.exitCode, result.messages, generated)
	}

	@Test
	fun `simple new entity mapping generates correct DSL`() {
		val result = compile("TestEntity.kt", "TestInput.kt", "TestMapper.kt")

		val generated = result.generatedSources["TestMapperImpl.kt"]
		assertTrue(generated != null, "TestMapperImpl.kt should be generated. Messages: ${result.messages}")
		assertTrue(generated!!.contains("name = input.name"), "Should map name. Generated:\n$generated")
		assertTrue(generated.contains("description = input.description"), "Should map description. Generated:\n$generated")
		assertTrue(generated.contains("TestEntity"), "Should use TestEntity DSL. Generated:\n$generated")
	}

	@Test
	fun `FK pattern generates cityId assignment`() {
		val result = compile("City.kt", "Shop.kt", "ShopMapper.kt")

		val generated = result.generatedSources["ShopMapperImpl.kt"]
		assertTrue(generated != null, "ShopMapperImpl.kt should be generated. Messages: ${result.messages}")
		assertTrue(generated!!.contains("this.cityId = cityId"), "Should generate FK assignment. Generated:\n$generated")
		assertTrue(generated.contains("name = name"), "Should map name. Generated:\n$generated")
	}

	@Test
	fun `base param generates copy DSL`() {
		val result = compile("Item.kt", "ItemMapper.kt")

		val generated = result.generatedSources["ItemMapperImpl.kt"]
		assertTrue(generated != null, "ItemMapperImpl.kt should be generated. Messages: ${result.messages}")
		assertTrue(generated!!.contains("Item(existing)"), "Should use copy DSL. Generated:\n$generated")
		assertTrue(generated.contains("price = price"), "Should map price. Generated:\n$generated")
	}

	@Test
	fun `explicit mapping uses custom source expressions`() {
		val result = compile("Product.kt", "ProductInput.kt", "ProductMapper.kt")

		val generated = result.generatedSources["ProductMapperImpl.kt"]
		assertTrue(generated != null, "ProductMapperImpl.kt should be generated. Messages: ${result.messages}")
		assertTrue(generated!!.contains("name = input.title"), "Should use explicit mapping. Generated:\n$generated")
		assertTrue(generated.contains("avatarKey = input.imageKey"), "Should use explicit mapping. Generated:\n$generated")
	}

	@Test
	fun `nested entity mapping generates Jimmer DSL block`() {
		val result = compile(
			"Localization.kt", "City.kt", "Subway.kt", "SubwayLine.kt",
			"LineEntry.kt", "SubwayLineMapper.kt",
		)

		val generated = result.generatedSources["SubwayLineMapperImpl.kt"]
		assertTrue(generated != null, "SubwayLineMapperImpl.kt should be generated. Messages: ${result.messages}")
		// Nested localization block
		assertTrue(generated!!.contains("localization = Localization"), "Should generate nested Localization. Generated:\n$generated")
		assertTrue(generated.contains("en = entry.en"), "Should map en inside nested. Generated:\n$generated")
		assertTrue(generated.contains("ru = entry.ru"), "Should map ru inside nested. Generated:\n$generated")
		// FK pattern for city
		assertTrue(generated.contains("this.cityId = cityId"), "Should generate FK for city. Generated:\n$generated")
		// Direct color mapping
		assertTrue(generated.contains("color = entry.color"), "Should map color. Generated:\n$generated")
	}

	@Test
	fun `collection element auto-discovery maps via sibling method`() {
		val result = compile(
			"Localization.kt", "City.kt", "Subway.kt", "SubwayLine.kt",
			"LineEntry.kt", "SubwayLineMapper.kt",
		)

		val generated = result.generatedSources["SubwayLineMapperImpl.kt"]
		assertTrue(generated != null, "SubwayLineMapperImpl.kt should be generated. Messages: ${result.messages}")
		// stations mapped via toSubway sibling method
		assertTrue(generated!!.contains("stations = entry.stations.map { toSubway(it) }"), "Should use element mapper. Generated:\n$generated")
	}

	@Test
	fun `nested entity in element mapper generates correct DSL`() {
		val result = compile(
			"Localization.kt", "City.kt", "Subway.kt", "SubwayLine.kt",
			"LineEntry.kt", "SubwayLineMapper.kt",
		)

		val generated = result.generatedSources["SubwayLineMapperImpl.kt"]
		assertTrue(generated != null, "SubwayLineMapperImpl.kt should be generated. Messages: ${result.messages}")
		// toSubway method should have nested localization
		assertTrue(generated!!.contains("fun toSubway(entry: StationEntry): Subway"), "Should generate toSubway method. Generated:\n$generated")
	}

	@Test
	fun `non-interface annotated with JimmerMapper fails`() {
		val result = compile("BadMapper.kt")

		assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
		assertTrue(result.messages.contains("can only be applied to interfaces"), result.messages)
	}

	@Test
	fun `non-entity return type fails`() {
		val result = compile("BadReturnMapper.kt")

		assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
		assertTrue(result.messages.contains("must be a Jimmer @Entity"), result.messages)
	}
}
