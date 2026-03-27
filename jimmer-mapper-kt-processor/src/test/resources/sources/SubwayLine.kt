import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator
import java.util.UUID

@Entity
interface SubwayLine {
	@Id
	@GeneratedValue(generatorType = UUIDIdGenerator::class)
	val id: UUID
	val color: String
	@ManyToOne
	val localization: Localization
	@ManyToOne
	val city: City
	@OneToMany(mappedBy = "line")
	val stations: List<Subway>
}
