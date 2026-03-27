import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper

data class NotAnEntity(val name: String)

@JimmerMapper
interface BadReturnMapper {
	fun toNew(name: String): NotAnEntity
}
