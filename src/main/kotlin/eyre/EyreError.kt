package eyre

class EyreError(
	val srcFile : SrcFile,
	val line    : Int,
	val message : String,
)