package eyre

import java.nio.file.Path



class Pos(var sec: Section, var disp: Int) {
	val addr get() = sec.addr + disp
	val pos get() = sec.pos + disp
	override fun toString() = "$sec:$disp"
	companion object { val NULL = Pos(Section.NULL, 0) }
}



class Reloc(
	val pos: Pos,
	val node: Node,
	val width: Width,
	val offset: Int,
	val rel: Boolean
)



data class Section(val index: Int, val name: String, val flags: UInt) {
	var pos = 0
	var addr = 0
	val present get() = addr != 0
	override fun toString() = name
	companion object { val NULL = Section(0, "", 0U) }
}



class SrcFile(val index: Int, val path: Path, val relPath: Path) {
	val tokens = ArrayList<Token>()
	val nodes = ArrayList<Node>()
	var lineCount = 0
	var invalid = false
	var resolved = false
	var resolving = false
}



class SrcPos(val file: SrcFile, val line: Int)



class EyreError(val srcPos: SrcPos?, override val message: String) : Exception()



enum class EyreStage {
	LEX,
	PARSE,
	RESOLVE,
	ASSEMBLE,
	LINK;
}



class Mem {
	var type = Type.GLOBAL
	var pos = 0
	var reg = 0
	enum class Type {
		GLOBAL,
		STACK,
		REGISTER
	}
}