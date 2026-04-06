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
		val generatedResources: Map<String, String>,
	)

	private fun source(name: String): SourceFile {
		val content = javaClass.getResource("/sources/$name")?.readText()
			?: error("Test source not found: /sources/$name")
		return SourceFile.kotlin(name, content)
	}

	private fun compile(vararg sourceNames: String): CompileResult =
		compileWithOptions(mapOf("jimmerMapper.framework" to "none"), *sourceNames)

	private fun compileWithOptions(options: Map<String, String>, vararg sourceNames: String): CompileResult {
		val sources = sourceNames.map { source(it) }
		val compilation = KotlinCompilation().apply {
			this.sources = sources
			inheritClassPath = true
			configureKsp(useKsp2 = true) {
				symbolProcessorProviders += JimmerMapperProcessorProvider()
				processorOptions.putAll(options)
			}
		}

		val result = compilation.compile()

		val generated = compilation.kspSourcesDir.walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.associate { it.name to it.readText() }

		val kspRoot = compilation.kspSourcesDir.parentFile
		val resources = kspRoot.walkTopDown()
			.filter { it.isFile && !it.extension.equals("kt", ignoreCase = true) }
			.associate { it.relativeTo(kspRoot).path to it.readText() }

		return CompileResult(result.exitCode, result.messages, generated, resources)
	}

	private fun compileTeamMapper(): CompileResult = compile(
		"Label.kt", "Region.kt", "Member.kt", "Team.kt",
		"TeamEntry.kt", "TeamMapper.kt",
	)

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
	fun `FK pattern generates regionId assignment`() {
		val result = compile("Region.kt", "Store.kt", "StoreMapper.kt")

		val generated = result.generatedSources["StoreMapperImpl.kt"]
		assertTrue(generated != null, "StoreMapperImpl.kt should be generated. Messages: ${result.messages}")
		assertTrue(generated!!.contains("this.regionId = regionId"), "Should generate FK assignment. Generated:\n$generated")
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
		val result = compileTeamMapper()

		val generated = result.generatedSources["TeamMapperImpl.kt"]
		assertTrue(generated != null, "TeamMapperImpl.kt should be generated. Messages: ${result.messages}")
		assertTrue(generated!!.contains("label = Label"), "Should generate nested Label. Generated:\n$generated")
		assertTrue(generated.contains("en = entry.en"), "Should map en inside nested. Generated:\n$generated")
		assertTrue(generated.contains("ru = entry.ru"), "Should map ru inside nested. Generated:\n$generated")
		assertTrue(generated.contains("this.regionId = regionId"), "Should generate FK for region. Generated:\n$generated")
		assertTrue(generated.contains("color = entry.color"), "Should map color. Generated:\n$generated")
	}

	@Test
	fun `collection element auto-discovery maps via sibling method`() {
		val result = compileTeamMapper()

		val generated = result.generatedSources["TeamMapperImpl.kt"]!!
		assertTrue(generated.contains("members = entry.members.map { toMember(it) }"), "Should use element mapper. Generated:\n$generated")
	}

	@Test
	fun `base without mergeCollections generates only new elements`() {
		val result = compileTeamMapper()

		val generated = result.generatedSources["TeamMapperImpl.kt"]!!
		val toUpdated = generated.substringAfter("fun toUpdated").substringBefore("fun toMerged")
		assertTrue(toUpdated.contains("members = members.map { toMember(it) }"), "Should map only new members in toUpdated. Generated:\n$generated")
		assertTrue(!toUpdated.contains("existing.members"), "Should NOT merge with existing in toUpdated. Generated:\n$generated")
	}

	@Test
	fun `base with mergeCollections true generates merged elements`() {
		val result = compileTeamMapper()

		val generated = result.generatedSources["TeamMapperImpl.kt"]!!
		assertTrue(generated.contains("existing.members + members.map { toMember(it) }"), "Should merge with existing in toMerged. Generated:\n$generated")
	}

	@Test
	fun `base with nested entity preserves existing child via copy DSL`() {
		val result = compileTeamMapper()

		val generated = result.generatedSources["TeamMapperImpl.kt"]!!

		// toBaseUpdated: @Base + nested label → copy DSL
		val toBaseUpdated = generated.substringAfter("fun toBaseUpdated").substringBefore("\n  override fun")
		assertTrue(
			toBaseUpdated.contains("label = Label(existing.label)"),
			"@Base + nested mapping should use copy DSL. toBaseUpdated:\n$toBaseUpdated",
		)
		assertTrue(
			toBaseUpdated.contains("en = entry.en"),
			"Should override en inside copy DSL. toBaseUpdated:\n$toBaseUpdated",
		)
		assertTrue(
			toBaseUpdated.contains("ru = entry.ru"),
			"Should override ru inside copy DSL. toBaseUpdated:\n$toBaseUpdated",
		)

		// toNew: NO @Base → plain Label (no copy DSL)
		val toNew = generated.substringAfter("fun toNew").substringBefore("\n  override fun")
		assertTrue(
			toNew.contains("label = Label {"),
			"Non-@Base should use plain DSL. toNew:\n$toNew",
		)
		assertTrue(
			!toNew.contains("existing.label"),
			"Non-@Base should NOT reference existing. toNew:\n$toNew",
		)

		// toUpdated: @Base but NO nested label mapping → no label override at all
		val toUpdated = generated.substringAfter("fun toUpdated").substringBefore("\n  override fun")
		assertTrue(
			!toUpdated.contains("label"),
			"@Base without nested mapping should NOT override label. toUpdated:\n$toUpdated",
		)

		// toMember: NO @Base, has nested label → plain DSL
		val toMember = generated.substringAfter("fun toMember")
		assertTrue(
			toMember.contains("label = Label {"),
			"toMember (no @Base) should use plain DSL. toMember:\n$toMember",
		)
	}

	@Test
	fun `base with multiple nested entities uses copy DSL for each`() {
		val result = compileTeamMapper()

		val generated = result.generatedSources["TeamMapperImpl.kt"]!!

		// toMultiNestedNew: no @Base → both label and region use plain DSL
		val toNew = generated.substringAfter("fun toMultiNestedNew").substringBefore("\n  override fun")
		assertTrue(toNew.contains("label = Label {"), "toMultiNestedNew should use plain Label DSL. toNew:\n$toNew")
		assertTrue(toNew.contains("region = Region {"), "toMultiNestedNew should use plain Region DSL. toNew:\n$toNew")
		assertTrue(!toNew.contains("existing"), "toMultiNestedNew should NOT reference existing. toNew:\n$toNew")

		// toMultiNestedUpdate: @Base → both label and region use copy DSL
		val toUpdate = generated.substringAfter("fun toMultiNestedUpdate").substringBefore("\n  override fun")
		assertTrue(toUpdate.contains("label = Label(existing.label)"), "Should use copy DSL for Label. toUpdate:\n$toUpdate")
		assertTrue(toUpdate.contains("region = Region(existing.region)"), "Should use copy DSL for Region. toUpdate:\n$toUpdate")
		assertTrue(toUpdate.contains("en = entry.en"), "Should map en inside Label copy. toUpdate:\n$toUpdate")
		assertTrue(toUpdate.contains("name = regionName"), "Should map name inside Region copy. toUpdate:\n$toUpdate")

		// toPartialNestedUpdate: @Base, only label mapped → label uses copy DSL, region NOT overridden
		val toPartial = generated.substringAfter("fun toPartialNestedUpdate").substringBefore("\n  override fun")
		assertTrue(toPartial.contains("label = Label(existing.label)"), "Should use copy DSL for Label. toPartial:\n$toPartial")
		assertTrue(!toPartial.contains("region"), "Should NOT override region (inherited from @Base). toPartial:\n$toPartial")
	}

	@Test
	fun `nested entity in element mapper generates correct DSL`() {
		val result = compileTeamMapper()

		val generated = result.generatedSources["TeamMapperImpl.kt"]
		assertTrue(generated != null, "TeamMapperImpl.kt should be generated. Messages: ${result.messages}")
		assertTrue(generated!!.contains("fun toMember(entry: MemberEntry): Member"), "Should generate toMember method. Generated:\n$generated")
	}

	@Test
	fun `generates reflect-config when nativeImage enabled`() {
		val result = compileWithOptions(
			options = mapOf("jimmerMapper.framework" to "none", "jimmerMapper.nativeImage" to "true"),
			"TestEntity.kt", "TestInput.kt", "TestMapper.kt",
		)

		val reflectConfig = result.generatedResources.entries
			.find { it.key.contains("reflect-config") }

		assertTrue(reflectConfig != null, "reflect-config.json should be generated. Resources: ${result.generatedResources.keys}. Sources: ${result.generatedSources.keys}")
		assertTrue(reflectConfig!!.value.contains("TestMapperImpl"), "Should contain generated class. Content:\n${reflectConfig.value}")
	}

	@Test
	fun `skips reflect-config by default`() {
		val result = compile("TestEntity.kt", "TestInput.kt", "TestMapper.kt")

		val reflectConfig = result.generatedResources.entries
			.find { it.key.contains("reflect-config") }

		assertTrue(reflectConfig == null, "reflect-config.json should NOT be generated by default. Resources: ${result.generatedResources.keys}")
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
