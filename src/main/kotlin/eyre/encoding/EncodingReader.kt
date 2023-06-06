package eyre.encoding

import eyre.*
import eyre.util.Unique
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

	val encodingLines = ArrayList<EncodingLine>()

	val expandedLines = ArrayList<EncodingLine>()



	fun readLines() {
		while(pos < string.length) {
			when(string[pos]) {
				'\n' -> { lineNumber++; pos++ }
				' '  -> pos++
				'\t' -> pos++
				'\r' -> pos++
				';'  -> skipLine()
				else -> try {
					encodingLines.add(readEncodingLine())
				} catch(e: Exception) {
					System.err.println("Error on line: $lineNumber")
					throw e
				}
			}
		}
	}



	fun expandLines() = encodingLines.forEach(::expandLine)

	fun readEncodings() = expandedLines.forEach(::readEncoding)



	private fun expandLine(line: EncodingLine) {
		when(line.ops) {
			"E_EM" -> {
				expandedLines += line.copy(ops = "MM_MMM")
				expandedLines += line.copy(prefix = Prefix.P66, ops = "X_XM")
				return
			}
			"E_I8" -> {
				expandedLines += line.copy(ops = "M_I8")
				expandedLines += line.copy(prefix = Prefix.P66, ops = "X_I8")
				return
			}
			"E_M" -> {
				expandedLines += line.copy(ops = "MM_M")
				expandedLines += line.copy(prefix = Prefix.P66, ops = "X_M")
				return
			}
		}

		if(line.mnemonic.endsWith("cc")) {
			for((postfix, opcodeInc) in Maps.ccList) {
				expandedLines += line.copy(
					mnemonic = line.mnemonic.dropLast(2) + postfix,
					opcode = line.opcode + opcodeInc
				)
			}
			return
		}

		expandedLines += line
	}



	private fun readEncodingLine(): EncodingLine {
		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var opcode = 0
		var extension = 0
		var mask = OpMask(0)
		var opsString = "NONE"
		var rexw = false
		var o16 = false

		while(true) {
			val value = (string[pos++].digitToInt(16) shl 4 or string[pos++].digitToInt(16))

			if(opcode == 0) {
				when(value) {
					0x66 -> if(escape == Escape.NONE) prefix = Prefix.P66 else opcode = 0x66
					0xF2 -> if(escape == Escape.NONE) prefix = Prefix.PF2 else opcode = 0xF2
					0xF3 -> if(escape == Escape.NONE) prefix = Prefix.PF3 else opcode = 0xF3
					0x9B -> if(escape == Escape.NONE) prefix = Prefix.P9B else opcode = 0x9B
					0x0F -> if(escape == Escape.NONE) escape = Escape.E0F else opcode = 0x0F
					0x38 -> if(escape == Escape.E0F) escape = Escape.E38 else opcode = 0x38
					0x3A -> if(escape == Escape.E0F) escape = Escape.E3A else opcode = 0x3A
					0x00 -> if(escape == Escape.E0F) escape = Escape.E00 else opcode = 0x00
					else -> opcode = value
				}
			} else {
				opcode = opcode or (value shl 8)
			}

			if(string[pos] == '/') {
				pos++
				extension = string[pos++].digitToInt()
				break
			}

			if(string[pos++] == ' ' && string[pos] == ' ') break
		}

		skipSpaces()
		val mnemonic = readWord()

		if(mnemonic == "WAIT") {
			opcode = 0x9B
			prefix = Prefix.NONE
		}

		skipSpaces()
		if(!atNewline()) {
			opsString = readWord()
			skipSpaces()
			while(!atNewline()) {
				val part = readWord()
				when {
					part[0] == '0' || part[0] == '1' -> mask = OpMask(part.toInt(2))
					part == "RW" -> rexw = true
					part == "O16" -> o16 = true
					else -> error("Invalid part: $part")
				}
				skipSpaces()
			}
		}

		skipLine()

		return EncodingLine(
			lineNumber,
			prefix,
			escape,
			extension,
			opcode,
			if(opcode and 0xFF00 != 0) 2 else 1,
			mnemonic,
			opsString,
			mask,
			rexw,
			o16
		)
	}



	private fun readEncoding(line: EncodingLine) {
		val multiOps = multiOpsMap[line.ops]
		val ops = opsMap[line.ops]

		if(multiOps == null && ops == null) {
			println(line.ops)
			return
		}

		val mask = multiOps?.mask ?: line.mask

		fun add(ops: Ops) {
			val mnemonic = mnemonics[line.mnemonic] ?: error("Missing mnemonic: ${line.mnemonic}")
			val encoding = Encoding(
				mnemonic, line.prefix, line.escape, line.opcode,
				line.extension, ops, mask, line.rexw.int, line.o16
			)
			groups.getOrPut(mnemonic) { EncodingGroup(mnemonic) }.add(encoding)
		}

		if(multiOps != null)
			for(part in multiOps.parts)
				add(part)
		else
			add(ops!!)
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