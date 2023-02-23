package eyre

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.notExists



class TempGroup(
	val mnemonic: String,
	val encodings: ArrayList<TempEncoding>,
	var customCount: Int,
	var operandsBits: Int
)



data class TempEncoding(
	val mnemonic  : String,
	val opcode    : Int,
	val extension : Int,
	val prefix    : Int,
	val operands  : Operands,
	val widths    : Widths
)



fun main() {
	EncodingGenerator(Paths.get("encodings.txt")).gen()
}



class EncodingGenerator(private val chars: CharArray) {


	constructor(path: Path) : this(Files.readString(path).toCharArray())



	private val groups = LinkedHashMap<String, TempGroup>()

	private val operandsMap = Operands.values().associateBy { it.name }

	private val compoundOperandsMap = CompoundOperands.values().associateBy { it.name }

	private val customOperandsMap = CustomOperands.values().associateBy { it.name }

	private var pos = 0

	private var lineCount = 1



	fun gen() {
		while(pos < chars.size) {
			when(chars[pos]) {
				'#'  -> skipLine()
				'\r' -> pos++
				'\n' -> { pos++; lineCount++ }
				' '  -> skipLine()
				'\t' -> skipLine()
				';'  -> break
				else -> readEncoding()
			}
		}

		val dirPath = Paths.get("prev")
		if(dirPath.notExists()) dirPath.createDirectory()
		val encodingsPath = Paths.get("src/main/kotlin/eyre/Encodings.kt")
		val backupPath = dirPath.resolve("Encodings.kt")
		if(encodingsPath.exists()) {
			Files.deleteIfExists(backupPath)
			Files.copy(encodingsPath, dirPath.resolve("Encodings.kt"))
		}

		Files.newBufferedWriter(encodingsPath).use {
			it.appendLine("package eyre\n")
			it.appendLine("enum class Mnemonic {")
			for((mnemonic, _) in groups)
				it.appendLine("\t$mnemonic,")
			it.appendLine("\t;")
			it.appendLine("\tval string = name.lowercase()")
			it.appendLine("}\n")

			it.appendLine("val groups = listOf(")
			for((_, group) in groups) {
				group.encodings.sortBy { e -> e.operands.ordinal }
				it.append("\tEncodingGroup(${group.operandsBits}, listOf(")
				for(encoding in group.encodings) {
					it.append("Encoding(${encoding.opcode}, ${encoding.extension}, ${encoding.prefix}, Widths(${encoding.widths.value}))")
					if(encoding != group.encodings.last()) it.append(", ")
				}
				it.appendLine(")),")
			}
			it.appendLine(")")

		}
	}



	private fun readEncoding() {
		var opcode = 0
		var extension = 0
		var prefix = 0

		while(true) {
			val char = chars[pos++]

			if(char.isWhitespace()) break

			if(char == '/') {
				extension = chars[pos++].digitToInt()
				break
			}

			opcode = (opcode shl 4) or char.digitToInt(16)
		}

		when(opcode and 0xFF) {
			0xF2, 0xF3, 0x66 -> {
				prefix = opcode and 0xFF
				opcode = opcode shr 8
			}
		}

		skipSpaces()
		val mnemonic = readUntil { it.isWhitespace() }
		skipSpaces()
		val operandsString = if(atNewline()) Operands.NONE.name else readUntil { it.isWhitespace() }
		skipSpaces()
		val widths = readWidths()

		operandsMap[operandsString]?.let {
			addEncoding(TempEncoding(mnemonic, opcode, extension, prefix, it, widths))
			return
		}

		customOperandsMap[operandsString]?.let {
			val operands = when(val count = groups[mnemonic]?.customCount ?: 0) {
				0    -> Operands.CUSTOM1
				1    -> Operands.CUSTOM2
				2    -> Operands.CUSTOM3
				else -> error("Too many custom operands ($count, line $lineCount) ${groups[mnemonic]?.encodings!!.joinToString()}")
			}

			addEncoding(TempEncoding(mnemonic, opcode, extension, prefix, operands, widths))
			return
		}

		compoundOperandsMap[operandsString]?.let {
			for(operands in it.operandsList)
				addEncoding(TempEncoding(mnemonic, opcode, extension, prefix, operands, widths))
			return
		}

		error("Invalid operands: $operandsString (line $lineCount)")
	}



	private fun readWidths(): Widths {
		if(atNewline()) return Widths(0)
		var value = 0

		for(i in 0 until 4) {
			when(chars[pos++]) {
				'1' -> value = value or (1 shl i)
				'0' -> continue
				else -> error("Invalid widths char (line $lineCount)")
			}
		}

		return Widths(value)
	}



	private fun addEncoding(encoding: TempEncoding) {
		val group = groups.getOrPut(encoding.mnemonic) { TempGroup(encoding.mnemonic, ArrayList(), 0, 0) }
		if(group.encodings.any { it.operands == encoding.operands }) return
		if(encoding.operands == Operands.CUSTOM1 || encoding.operands == Operands.CUSTOM2)
			group.customCount++
		group.encodings.add(encoding)
		group.operandsBits = group.operandsBits or (1 shl encoding.operands.ordinal)
	}



	private fun atNewline() = chars[pos] == '\r' || chars[pos] == '\n'



	private fun readUntil(predicate: (Char) -> Boolean) = buildString {
		while(!predicate(chars[pos]))
			append(chars[pos++])
	}



	private fun skipLine() {
		while(chars[pos] != '\n') pos++
	}



	private fun skipSpaces() {
		while(chars[pos] == ' ' || chars[pos] == '\t') pos++
	}


}