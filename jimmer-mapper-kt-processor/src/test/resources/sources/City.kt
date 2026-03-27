import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator
import java.util.UUID

@Entity
interface City {
	@Id
	@GeneratedValue(generatorType = UUIDIdGenerator::class)
	val id: UUID
	val name: String
}
