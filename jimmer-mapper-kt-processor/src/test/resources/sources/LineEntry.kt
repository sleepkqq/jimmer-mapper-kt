data class StationEntry(
	val en: String,
	val ru: String,
)

data class LineEntry(
	val en: String,
	val ru: String,
	val color: String,
	val stations: List<StationEntry>,
)
