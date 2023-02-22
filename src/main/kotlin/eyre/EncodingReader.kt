package eyre

import java.nio.file.Files
import java.nio.file.Path



class TempGroup(
	val mnemonic: String,
	val encodings: ArrayList<TempEncoding>,
	var customCount: Int
)



data class TempEncoding(
	val mnemonic  : String,
	val opcode    : Int,
	val extension : Int,
	val prefix    : Int,
	val operands  : Operands,
	val widths    : Widths
)



class EncodingGenerator(private val chars: CharArray) {


	constructor(path: Path) : this(Files.readString(path).toCharArray())



	private val groups = HashMap<String, TempGroup>()

	private val operandsMap = Operands.values().associateBy { it.name }

	private val compoundOperandsMap = CompoundOperands.values().associateBy { it.name }

	private val customOperandsMap = CustomOperands.values().associateBy { it.name }

	private var pos = 0

	private var lineCount = 1



	fun gen() {
		read()
	}



	private fun read() {
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

		skipWhitespace()
		val mnemonic = readUntil { it.isWhitespace() }
		skipWhitespace()
		val operandsString = if(atNewline()) Operands.NONE.name else readUntil { it.isWhitespace() }
		val widths = readWidths()

		operandsMap[operandsString]?.let {
			addEncoding(TempEncoding(mnemonic, opcode, extension, prefix, it, widths))
			return
		}

		customOperandsMap[operandsString]?.let {
			val operands = when(val count = groups[mnemonic]?.customCount ?: 0) {
				0    -> Operands.CUSTOM1
				1    -> Operands.CUSTOM2
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

		while(true) {
			val char = chars[pos++]

			if(char.isWhitespace()) break

			value = when(char) {
				'0'  -> value shl 2
				'1'  -> (value shl 2) or 1
				else -> error("Invalid widths char (line $lineCount)")
			}
		}

		return Widths(value)
	}



	private fun addEncoding(encoding: TempEncoding) {
		val group = groups.getOrPut(encoding.mnemonic) { TempGroup(encoding.mnemonic, ArrayList(), 0) }
		if(encoding.operands == Operands.CUSTOM1 || encoding.operands == Operands.CUSTOM2)
			group.customCount++
		group.encodings.add(encoding)
	}



	private fun atNewline() = chars[pos] == '\r' || chars[pos] == '\n'



	private fun readUntil(predicate: (Char) -> Boolean) = buildString {
		while(!predicate(chars[pos]))
			append(chars[pos++])
	}



	private fun skipLine() {
		while(chars[pos] != '\n') pos++
	}



	private fun skipWhitespace() {
		while(chars[pos].isWhitespace()) pos++
	}


}