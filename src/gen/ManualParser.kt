package eyre.gen

import eyre.*
import java.nio.file.Files
import java.nio.file.Paths

class ManualParser(private val chars: CharArray) {


	constructor(path: String) : this(Files.readString(Paths.get(path)).let {
		val chars = CharArray(it.length + 4)
		it.toCharArray(chars)
		chars.fill('\n', fromIndex = it.length)
		chars
	})



	private var pos = 0

	private var lineNum = 1

	private val opsMap = Ops.entries.associateBy { it.name }

	private val multiOpsMap = MultiOps.entries.associateBy { it.name }

	val lines = ArrayList<ManualLine>()

	val encs = ArrayList<ManualEnc>()



	/*
	Public functions
	 */



	fun parseAndConvert(): List<ManualEnc> {
		parseLines()
		convertToEncs()
		return encs
	}

	fun parseLines() {
		try {
			while(pos < chars.size) when(chars[pos]) {
				'\n' -> { pos++; lineNum++ }
				' '  -> skipLine()
				'\r' -> pos++
				'#'  -> skipLine()
				else -> lines.add(parseLine())
			}
		} catch(e: Exception) {
			System.err.println("Error on line $lineNum")
			throw e
		}
	}

	fun convertToEncs() {
		for(line in lines) {
			if(line.ops in opsMap || line.ops in multiOpsMap) {
				line.toEncs()
			}
		}
	}



	/*
	Private functions
	 */



	private fun skipSpaces() {
		while(chars[pos] == ' ') pos++
	}

	private fun readPart(): String {
		val start = pos
		while(!chars[pos].isWhitespace()) pos++
		return String(chars, start, pos - start)
	}

	private fun skipLine() {
		while(chars[pos] != '\n') pos++
	}

	private fun atNewline() = (chars[pos] == '\n' || chars[pos] == '\r')



	private fun ManualLine.toEncs() {
		fun add(mnemonic: String, opcode: Int, ops: Ops) = ManualEnc(
			mnemonic,
			prefix,
			escape,
			opcode,
			ext,
			mask,
			ops,
			rw,
			o16
		).let(encs::add)

		fun add(mnemonic: String, opcode: Int) {
			val multi = multiOpsMap[ops]
			if(multi != null)
				for(part in multi.parts)
					add(mnemonic, opcode, part)
			else
				add(mnemonic, opcode, opsMap[ops] ?: error("Invalid ops: $ops"))
		}

		if(mnemonic.endsWith("cc"))
			for((postfix, opcodeInc) in ccList)
				add(mnemonic.dropLast(2) + postfix, opcode + opcodeInc)
		else
			add(mnemonic, opcode)
	}



	private fun parseLine(): ManualLine {
		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var opcode = 0
		var ext = -1
		var mask = 0
		var ops = "NONE"
		var rw = 0
		var o16 = 0
		val mnemonic: String
		var pseudo = -1
		var noRw = 0

		while(true) {
			val part = readPart()

			if(part.length == 4 && part[2] == '/') {
				ext = part[3].digitToInt()
			} else if(part.length > 2 || !part[0].isHex || !part[1].isHex) {
				mnemonic = part
				break
			}

			val value = (part[0].digitToInt(16) shl 4) or part[1].digitToInt(16)

			if(opcode == 0) {
				when(value) {
					0x66 -> if(escape == Escape.NONE) prefix = Prefix.P66 else opcode = 0x66
					0xF2 -> if(escape == Escape.NONE) prefix = Prefix.PF2 else opcode = 0xF2
					0xF3 -> if(escape == Escape.NONE) prefix = Prefix.PF3 else opcode = 0xF3
					0x9B -> if(escape == Escape.NONE) prefix = Prefix.P9B else opcode = 0x9B
					0x0F -> if(escape == Escape.NONE) escape = Escape.E0F else opcode = 0x0F
					0x38 -> if(escape == Escape.E0F) escape = Escape.E38 else opcode = 0x38
					0x3A -> if(escape == Escape.E0F) escape = Escape.E3A else opcode = 0x3A
					else -> opcode = value
				}
			} else {
				opcode = opcode or (value shl 8)
			}

			skipSpaces()
		}

		if(prefix == Prefix.P9B && opcode == 0) {
			opcode = 0x9B
			prefix = Prefix.NONE
		}

		skipSpaces()

		var firstPart = true

		while(!atNewline()) {
			val part = readPart()
			if(part.isEmpty()) error("Empty part")
			when {
				part == "RW" -> rw = 1
				part == "O16" -> o16 = 1
				part == "NORW" -> noRw = 1
				part[0] == '0' || part[0] == '1' -> mask = part.toInt(2)
				part[0] == ':' -> pseudo = part.drop(1).toInt()
				!firstPart -> error("Invalid part: $part")
				else -> ops = part
			}
			firstPart = false
			skipSpaces()
		}

		return ManualLine(
			lineNum,
			mnemonic,
			prefix,
			escape,
			opcode,
			ext,
			ops,
			mask,
			rw,
			o16,
			noRw,
			pseudo
		)
	}


}