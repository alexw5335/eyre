package eyre

import java.nio.file.Path



data class DllImport(val name: String) {
	val imports = HashMap<String, Pos>()
}



data class SrcFile(val index: Int, val path: Path, val relPath: Path) {
	val tokens = ArrayList<Token>()
	val nodes = ArrayList<Node>()
	var lineCount = 0
	var invalid = false
	var resolved = false
	var resolving = false
}

data class Section(val index: Int, val name: String, val flags: UInt) {
	var pos = 0
	var addr = 0
	var size = 0
	val present get() = addr != 0
	companion object { val NULL = Section(0, "", 0U) }
}

data class Pos(var sec: Section, var disp: Int) {
	val addr get() = sec.addr + disp
	val pos get() = sec.pos + disp
	companion object {
		val NULL = Pos(Section.NULL, 0)
	}
}

class RelReloc(
	val srcPos: SrcPos?,
	val pos: Pos,
	val sym: PosSym,
	val immWidth: Width
)

class AbsReloc(
	val pos: Pos,
	val node: Node,
)

class Reloc(
	val pos    : Pos,
	val node   : Node,
	val width  : Width,
	val offset : Int,
	val rel    : Boolean
)

data class SrcPos(val file: SrcFile, val line: Int)

class EyreError(val srcPos: SrcPos?, override val message: String) : Exception()

enum class EyreStage {
	NONE,
	LEX,
	PARSE,
	RESOLVE,
	ASSEMBLE,
	LINK;
}