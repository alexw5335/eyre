package eyre

class EncParser(val chars: CharArray) {


	private var lineNum = 1

	private var pos = 0



	fun parse() {
		try {
			while(pos < chars.size) {
				when(chars[pos]) {
					'\n' -> { pos++; lineNum++ }
					'\r' -> pos++
					'#'  -> skipLine()
					else -> parseLine()
				}
			}
		} catch(e: Exception) {
			System.err.println("Error on line $lineNum  ---  ${getLine(lineNum)}")
			e.printStackTrace()
		}
	}



	private fun atNewline() = pos >= chars.size || chars[pos] == '\r' || chars[pos] == '\n'

	private fun getLine(target: Int): String {
		var pos = 0
		var lineNum = 1
		while(pos < chars.size) {
			if(chars[pos++] != '\n') continue
			if(++lineNum == target) break
		}
		val start = pos
		var length = 0
		while(pos < chars.size && chars[pos++] != '\n') length++
		return String(chars, start, length)
	}

	private fun skipLine() {
		while(pos < chars.size && chars[pos++] != '\n') Unit
		lineNum++
	}

	private fun skipSpaces() {
		while(pos < chars.size && chars[pos] == ' ') pos++
	}

	private fun readPart(): String {
		val start = pos
		while(pos < chars.size && !chars[pos].isWhitespace()) pos++
		return String(chars, start, pos - start)
	}



	private fun parseLine() {
		var vw = 0
		var vl = 0
		var ext = -1
		var opcode = 0
		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var mask = 0
		var pseudo = -1
		var o16 = false
		var rw = false
		var a32 = false
		var mr = false

		fun addOpcode(part: Int) {
			opcode = when {
				opcode and 0xFF00 != 0 -> error("Too many opcode parts")
				opcode != 0 -> opcode or (part shl 8)
				else -> part
			}
		}

		if(chars[pos] == 'W') {
			pos++
			vw = when(chars[pos++]) {
				'1' -> 1 '0', 'G' -> 0 else -> error("Invalid VEX.W")
			}
		}

		skipSpaces()

		if(chars[pos] == 'L') {
			pos++
			vl = when(chars[pos++]) {
				'1' -> 1 '0', 'L', 'G' -> 0 else -> error("Invalid VEX.L")
			}
		}

		skipSpaces()

		while(true) {
			if(chars[pos] == 'N' && chars[pos + 1] == 'P') {
				pos += 2
				skipSpaces()
				continue
			}

			if(!chars[pos].isHex || !chars[pos + 1].isHex || (chars[pos + 2] != ' ' && chars[pos + 2] != '/'))
				break

			val part = (chars[pos++].digitToInt(16) shl 4) or chars[pos++].digitToInt(16)

			if(chars[pos] == '/') {
				pos++
				ext = chars[pos++].digitToInt()
			}

			when(part) {
				0x66 -> prefix = Prefix.P66
				0xF2 -> prefix = Prefix.PF2
				0xF3 -> prefix = Prefix.PF3
				0x9B -> prefix = Prefix.P9B
				0x0F -> if(escape == Escape.NONE) escape = Escape.E0F else addOpcode(part)
				0x38 -> if(escape == Escape.E0F) escape = Escape.E38 else addOpcode(part)
				0x3A -> if(escape == Escape.E0F) escape = Escape.E3A else addOpcode(part)
				else -> addOpcode(part)
			}

			skipSpaces()
		}

		if(prefix == Prefix.P9B && opcode == 0) {
			prefix = Prefix.NONE
			opcode = 0x9B
		}

		val mnemonicString = readPart()
		skipSpaces()
		if(atNewline()) {
			return
		}
		val opsString = readPart()
		skipSpaces()

		while(!atNewline()) {
			val part = readPart()
			when {
				part[0] == '0' || part[0] == '1' -> mask = part.toInt(2)
				part[0] == ':' -> pseudo = part.drop(1).toInt()
				part == "RW" -> rw = true
				part == "O16" -> o16 = true
				part == "A32" -> a32 = true
				part == "MR" -> mr = true
				else -> error("Invalid part: $part")
			}
			skipSpaces()
		}

		if(mask != 0)
			println(mnemonicString)

		skipLine()
	}


}


enum class CustomOps {
	A_I,
	RM_I,
	RM_I8,
	RM_R,
	R_RM,
	RM,
	O,
	R_RM_I8,
	R_RM_I,
	A_O,
	O_A,
	R_SEG,
	M_SEG,
	SEG_R,
	SEG_M,
	O_I,
	R_CR,
	R_DR,
	CR_R,
	DR_R,
	R_MEM,
	RM_ONE,
	RM_CL,
	A_I8,
	A_DX,
	I8_A,
	DX_A,
	M,
	R,
	RM_R_I8,
	RM_R_CL,
	M_R,
	R_M,
}