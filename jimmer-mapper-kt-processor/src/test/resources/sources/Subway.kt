import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator
import java.util.UUID

@Entity
interface Subway {
	@Id
	@GeneratedValue(generatorType = UUIDIdGenerator::class)
	val id: UUID
	@ManyToOne
	val localization: Localization
	@ManyToOne
	val line: SubwayLine
}
