import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper
import com.sleepkqq.jimmer.mapper.annotation.Base

@JimmerMapper
interface ItemMapper {
	fun toUpdated(@Base existing: Item, price: Int): Item
}
