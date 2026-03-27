import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper
import com.sleepkqq.jimmer.mapper.annotation.Mapping

@JimmerMapper
interface ProductMapper {
	@Mapping(source = "input.title", target = "name")
	@Mapping(source = "input.imageKey", target = "avatarKey")
	fun toNew(input: ProductInput): Product
}
