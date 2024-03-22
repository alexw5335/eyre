package eyre

import java.nio.file.Files
import java.nio.file.Path


data class Section(
	val index: Int,
	val name: String,
	val flags: UInt,
	val writer: BinWriter?,
) {
	var pos = 0
	var addr = 0
	var size = 0
	val present get() = addr != 0
	companion object { val NULL = Section(0, "", 0U, null) }
}

sealed class SrcFile(val name: String) {
	val nodes = ArrayList<Node>()
	var lineCount = 0
	var invalid = false
	var resolving = false
	var resolved = false
	abstract fun codeSize(): Int
	abstract fun readCode(dst: CharArray)
}

class ProjectSrcFile(name: String, val absPath: Path) : SrcFile(name) {
	override fun codeSize() = Files.size(absPath).toInt()
	override fun readCode(dst: CharArray) { Files.newBufferedReader(absPath).use { it.read(dst) } }
}

class VirtualSrcFile(name: String, val code: String) : SrcFile(name) {
	override fun codeSize() = code.length
	override fun readCode(dst: CharArray) { code.toCharArray(dst) }
}

data class SrcPos(val file: SrcFile, val line: Int)

class EyreError(val srcPos: SrcPos?, override val message: String) : Exception()

class SecPos(var sec: Section = Section.NULL, var disp: Int = 0) {
	val totalPos get() = sec.pos + disp
	val totalAddr get() = sec.addr + disp
}

class DllImport(val name: Name, val pos: SecPos)

class Dll(val name: Name, val imports: HashMap<Name, DllImport>)

/**
 * - [pos] is where the relocation will be written
 * - [immWidth] is only used by memory operands followed by an immediate operand
 * - [width] is only used by REL operands (QWORD for AbsReloc, DWORD for MemReloc)
 */
class Reloc(
	val pos      : SecPos,
	val target   : SecPos,
	val disp     : Int,
	val immWidth : Width, // Only used by memory operand relocations
	val width    : Width  // Only used by rel operand relocations
)