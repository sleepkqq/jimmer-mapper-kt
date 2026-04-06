import com.sleepkqq.jimmer.mapper.annotation.Base
import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper
import com.sleepkqq.jimmer.mapper.annotation.Mapping
import java.util.UUID

@JimmerMapper
interface TeamMapper {

	@Mapping(source = "entry.en", target = "label.en")
	@Mapping(source = "entry.ru", target = "label.ru")
	@Mapping(source = "entry.members", target = "members")
	fun toNew(entry: TeamEntry, regionId: UUID): Team

	fun toUpdated(@Base existing: Team, members: List<MemberEntry>, regionId: UUID): Team

	fun toMerged(@Base(mergeCollections = true) existing: Team, members: List<MemberEntry>, regionId: UUID): Team

	@Mapping(source = "entry.en", target = "label.en")
	@Mapping(source = "entry.ru", target = "label.ru")
	@Mapping(source = "entry.members", target = "members")
	fun toBaseUpdated(@Base existing: Team, entry: TeamEntry): Team

	@Mapping(source = "entry.en", target = "label.en")
	@Mapping(source = "entry.ru", target = "label.ru")
	@Mapping(source = "regionName", target = "region.name")
	fun toMultiNestedUpdate(@Base existing: Team, entry: TeamEntry, regionName: String): Team

	@Mapping(source = "entry.en", target = "label.en")
	@Mapping(source = "entry.ru", target = "label.ru")
	@Mapping(source = "regionName", target = "region.name")
	fun toMultiNestedNew(entry: TeamEntry, regionName: String): Team

	@Mapping(source = "entry.en", target = "label.en")
	@Mapping(source = "entry.ru", target = "label.ru")
	fun toPartialNestedUpdate(@Base existing: Team, entry: TeamEntry): Team

	@Mapping(source = "entry.en", target = "label.en")
	@Mapping(source = "entry.ru", target = "label.ru")
	fun toMember(entry: MemberEntry): Member
}
