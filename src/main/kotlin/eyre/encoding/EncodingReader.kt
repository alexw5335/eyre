package eyre.encoding

class EncodingReader(private val string: String) {


	private var pos = 0

	private var lineNumber = 1

	private val encodings = ArrayList<Encoding>()



	fun read() {
		while(pos < string.length) {
			when(string[pos]) {
				'\n' -> { lineNumber++; pos++ }
				' '  -> pos++
				'\t' -> pos++
				'\r' -> pos++
				';'  -> skipLine()
				else -> try {
					readEncoding()
				} catch(e: Exception) {
					System.err.println("Error on line: $lineNumber")
					throw e
				}
			}
		}

		encodings.forEach(::println)
	}



	private fun readEncoding() {
		var prefix      = 0 // 66, F2, F3
		var opcode      = 0
		var oplen       = 0
		var extension   = 0
		var widths      = Widths(0)
		var cops: COps? = null
		var ops: Ops?   = null
		var opsString   = "NONE"
		val parts       = ArrayList<String>()

		while(true) {
			val value = (string[pos++].digitToInt(16) shl 4) or string[pos++].digitToInt(16)

			if(opcode == 0 && value == 0x66 || value == 0xF2 || value == 0xF3)
				prefix = value
			else
				opcode = opcode or (value shl (oplen++ shl 3))

			if(string[pos] == '/') {
				pos++
				extension = string[pos++].digitToInt()
				break
			}

			if(string[pos++] == ' ' && string[pos] == ' ') break
		}

		skipSpaces()
		val mnemonic = readWord()

		when(mnemonic) {
			"MOVSX", "MOVZX", "MOVSXD",
			"CRC32", "LAR", "LSL",
			"INVEPT", "INVPCID", "INVVPID",
			"ENQCMD", "ENQCMDS", "MOVDIR64B"
				-> { skipLine(); return }
		}

		skipSpaces()
		if(!atNewline()) {
			opsString = readWord()
			skipSpaces()
			while(!atNewline()) {
				parts += readWord()
				skipSpaces()
			}
		}

		when(opsString) {
			in COps.map -> cops = COps.map[opsString]!!
			in Ops.map  -> ops = Ops.map[opsString]!!
			"MEM"  -> { ops = Ops.M; widths = Widths.NONE }
			"M16"  -> { ops = Ops.M; widths = Widths.WORD }
			"M32"  -> { ops = Ops.M; widths = Widths.DWORD }
			"M64"  -> { ops = Ops.M; widths = Widths.QWORD }
			"M80"  -> { ops = Ops.M; widths = Widths.TWORD }
			"M128" -> { ops = Ops.M; widths = Widths.XWORD }
			else   -> error("Invalid operands: $opsString")
		}

		fun add(mnemonic: String, opcode: Int, ops: Ops) = encodings.add(Encoding(
			mnemonic, prefix, opcode, oplen, extension, ops, widths
		))

		if(mnemonic.endsWith("cc")) {
			for((postfix, opcodeInc) in ccList) {
				val mnemonic2 = mnemonic.dropLast(2) + postfix
				val opcode2 =  opcode + (opcodeInc shl ((oplen - 1) shl 3))
				if(cops != null)
					for(part in cops.parts)
						add(mnemonic2, opcode2, part)
				else
					add(mnemonic2, opcode2, ops!!)
			}
		} else {
			if(cops != null)
				for(part in cops.parts)
					add(mnemonic, opcode, part)
			else
				add(mnemonic, opcode, ops!!)
		}

		skipLine()
	}



	private fun atNewline() = pos >= string.length || string[pos] == '\r' || string[pos] == '\n'

	private fun readWord() = buildString {
		while(pos < string.length && !string[pos].isWhitespace())
			append(string[pos++])
	}

	private fun skipSpaces() {
		while(pos < string.length && string[pos] == ' ')
			pos++
	}

	private fun skipLine() {
		while(pos < string.length && string[pos] != '\n')
			pos++
	}


}