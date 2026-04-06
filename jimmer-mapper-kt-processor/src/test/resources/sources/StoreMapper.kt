import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper
import java.util.UUID

@JimmerMapper
interface StoreMapper {
	fun toNew(name: String, regionId: UUID): Store
}
