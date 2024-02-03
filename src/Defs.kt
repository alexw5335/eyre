package eyre

import java.nio.file.Path



class SrcFile(val index: Int, val path: Path, val relPath: Path) {
	val tokens = ArrayList<Token>()
	val nodes = ArrayList<Node>()
	var lineCount = 0
	var invalid = false
	var resolved = false
	var resolving = false
}

class Pos(val section: Section, val disp: Int)

class Reloc(
	val pos: Pos,
	val node: Node,
	val width: Width,
	val offset: Int,
	val rel: Boolean
)

class SrcPos(val file: SrcFile, val line: Int)

class EyreError(val srcPos: SrcPos?, override val message: String) : Exception()

data class Section(val index: Int, val name: String, val flags: UInt) {
	var pos = 0
	var addr = 0
	val present get() = addr != 0
	override fun toString() = name
	companion object { val NULL = Section(0, "", 0U) }
}

enum class EyreStage {
	NONE,
	LEX,
	PARSE,
	RESOLVE,
	ASSEMBLE,
	LINK;
}