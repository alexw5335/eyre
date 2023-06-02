package eyre.encoding

import eyre.*
import eyre.util.int
import java.nio.file.Files
import java.nio.file.Paths
import java.util.EnumMap

class EncodingReader(private val string: String) {


	companion object {
		fun create(path: String) = EncodingReader(Files.readString(Paths.get(path)))
	}



	private var pos = 0

	private var lineNumber = 1

	private val mnemonics = Mnemonic.values.associateBy { it.name }

	val encodings = ArrayList<Encoding>()

	val groups = EnumMap<Mnemonic, EncodingGroup>(Mnemonic::class.java)

	private val multiOpsMap = MultiOps.values().associateBy { it.name }

	private val opsMap = Ops.values().associateBy { it.name }



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

		for(group in groups.values)
			group.encodings.sortBy { it.operands }
	}



	private fun readEncoding() {
		var prefix      = 0 // 66, 67, F2, F3
		var escape      = 0
		var opcode      = 0
		var oplen       = 0
		var extension   = 0
		var opMask      = OpMask(0)
		var opMask2     = OpMask(0)
		var opsString   = "NONE"
		val parts       = ArrayList<String>()
		var rexw        = false
		var o16         = false
		var widthOp     = 0

		while(true) {
			val value = (string[pos++].digitToInt(16) shl 4) or string[pos++].digitToInt(16)

			fun setOpcode() {
				opcode = opcode or (value shl (oplen++ shl 3))
			}

			if(opcode == 0)
				when(value) {
					0x66, 0x67, 0xF2, 0xF3, 0x9B -> prefix = value
					0x0F -> escape = 1
					0x38 -> if(escape == 1) escape = 2 else setOpcode()
					0x3A -> if(escape == 1) escape = 3 else setOpcode()
					0x00 -> if(escape == 1) escape = 4 else setOpcode()
					else -> setOpcode()
				}
			else
				setOpcode()


			if(string[pos] == '/') {
				pos++
				extension = string[pos++].digitToInt()
				break
			}

			if(string[pos++] == ' ' && string[pos] == ' ') break
		}

		skipSpaces()
		val mnemonicString = readWord()

		if(mnemonicString == "WAIT") {
			opcode = 0x9B
			prefix = 0
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

		val multiOps = multiOpsMap[opsString]
		val ops = opsMap[opsString]

		if(multiOps == null && ops == null)
			error("Unrecognised operands: $opsString")

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
				"O16" -> o16 = true
				else  -> error("Invalid part: $part")
			}
		}

		if(multiOps != null) {
			if(multiOps.mask1 != null) {
				if(opMask.isNotEmpty) opMask2 = opMask
				opMask = multiOps.mask1
				widthOp = 1
			} else if(multiOps.mask2 != null) {
				opMask2 = multiOps.mask2
				widthOp = 0
			}
		}

		fun add(mnemonicString: String, opcode: Int, ops: Ops) {
			val mnemonic = mnemonics[mnemonicString] ?: error("Missing mnemonic: $mnemonicString")

			val encoding = Encoding(
				mnemonic, prefix, escape, opcode, oplen, 
				extension, ops, opMask, opMask2, rexw.int,
				o16, widthOp
			)

			encodings += encoding

			groups.getOrPut(mnemonic) { EncodingGroup(mnemonic) }.add(encoding)
		}

		if(mnemonicString.endsWith("cc")) {
			for((postfix, opcodeInc) in Maps.ccList) {
				val mnemonicString2 = mnemonicString.dropLast(2) + postfix
				val opcode2 =  opcode + (opcodeInc shl ((oplen - 1) shl 3))
				if(multiOps != null)
					for(part in multiOps.parts)
						add(mnemonicString2, opcode2, part)
				else
					add(mnemonicString2, opcode2, ops!!)
			}
		} else {
			if(multiOps != null)
				for(part in multiOps.parts)
					add(mnemonicString, opcode, part)
			else
				add(mnemonicString, opcode, ops!!)
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