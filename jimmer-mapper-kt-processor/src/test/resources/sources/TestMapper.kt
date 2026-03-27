import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper

@JimmerMapper
interface TestMapper {
	fun toNew(input: TestInput): TestEntity
}
