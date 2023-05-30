package eyre.encoding

import eyre.Mnemonic
import eyre.OpMask
import java.nio.file.Files
import java.nio.file.Paths

class EncodingReader(private val string: String) {


	private var pos = 0

	private var lineNumber = 1

	private val mnemonicMap = Mnemonic.values.associateBy { it.name }

	val encodings = ArrayList<ParsedEncoding>()



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
	}



	private fun readEncoding() {
		var prefix      = 0 // 66, F2, F3
		var opcode      = 0
		var oplen       = 0
		var extension   = 0
		var opMask      = OpMask(0)
		var opMask2     = OpMask(0)
		var cops: Cops? = null
		var ops: Ops?   = null
		var opsString   = "NONE"
		val parts       = ArrayList<String>()
		var rexw = false
		var rexr = false
		var o16 = false
		var a32 = false

		while(true) {
			val value = (string[pos++].digitToInt(16) shl 4) or string[pos++].digitToInt(16)

			if(opcode == 0 && value == 0x66 || value == 0xF2 || value == 0xF3 || value == 0x9B)
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
			in Cops.map -> cops = Cops.map[opsString]!!
			in Ops.map  -> ops = Ops.map[opsString]!!
			else        -> error("Invalid operands: $opsString")
		}

		for(part in parts) {
			val intValue = part.toIntOrNull(2)

			if(intValue != null) {
				if(opMask.isNotEmpty)
					opMask2 = OpMask(intValue)
				else
					opMask = OpMask(intValue)
				continue
			}

			when(part) {
				"RW"  -> rexw = true
				"RR"  -> rexr = true
				"O16" -> o16 = true
				"A32" -> a32 = true
				else  -> error("Invalid part: $part")
			}
		}

		if(cops != null) {
			if(cops.mask1 != null) {
				if(opMask.isNotEmpty) opMask2 = opMask
				opMask = cops.mask1!!
			} else if(cops.mask2 != null) {
				opMask2 = cops.mask2!!
			}
		}

		fun add(mnemonic: String, opcode: Int, ops: Ops) = encodings.add(ParsedEncoding(
			mnemonicMap[mnemonic] ?: error("Missing mnemonic: $mnemonic"),
			prefix, opcode, oplen, extension, ops,
			opMask, opMask2, rexw, rexr, o16, a32
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