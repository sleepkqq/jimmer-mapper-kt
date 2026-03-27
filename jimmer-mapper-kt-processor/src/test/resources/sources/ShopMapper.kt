import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper
import java.util.UUID

@JimmerMapper
interface ShopMapper {
	fun toNew(name: String, cityId: UUID): Shop
}
