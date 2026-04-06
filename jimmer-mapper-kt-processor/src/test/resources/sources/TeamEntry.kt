data class MemberEntry(
	val en: String,
	val ru: String,
)

data class TeamEntry(
	val en: String,
	val ru: String,
	val color: String,
	val members: List<MemberEntry>,
)
