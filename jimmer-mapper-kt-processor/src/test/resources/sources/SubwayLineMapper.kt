import com.sleepkqq.jimmer.mapper.annotation.Base
import com.sleepkqq.jimmer.mapper.annotation.JimmerMapper
import com.sleepkqq.jimmer.mapper.annotation.Mapping
import java.util.UUID

@JimmerMapper
interface SubwayLineMapper {

	@Mapping(source = "entry.en", target = "localization.en")
	@Mapping(source = "entry.ru", target = "localization.ru")
	@Mapping(source = "entry.stations", target = "stations")
	fun toNew(entry: LineEntry, cityId: UUID): SubwayLine

	fun toUpdated(@Base existing: SubwayLine, stations: List<StationEntry>, cityId: UUID): SubwayLine

	fun toMerged(@Base(mergeCollections = true) existing: SubwayLine, stations: List<StationEntry>, cityId: UUID): SubwayLine

	@Mapping(source = "entry.en", target = "localization.en")
	@Mapping(source = "entry.ru", target = "localization.ru")
	fun toSubway(entry: StationEntry): Subway
}
