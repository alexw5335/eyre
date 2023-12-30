package eyre

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

class EncParser(path: String) {


	private val chars = Files.readString(Paths.get(path))

	private var pos = 0

	private var lineNum = 0



	private fun skipLine() {
		while(pos < chars.length && chars[pos] != '\n') pos++
	}

	private fun skipSpaces() {
		while(pos < chars.length && chars[pos] == ' ') pos++
	}

	private fun readPart(): String {
		val start = pos
		while(pos < chars.length && !chars[pos].isWhitespace()) pos++
		return chars.substring(start, pos)
	}

	private val Char.isHex get() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

	private val Char.toHex get() = digitToIntOrNull(16) ?: error("Invalid hex char: $this")



	fun parse() {
		lineNum = 1

		try {
			while(pos < chars.length) {
				when(chars[pos]) {
					' ', '\r', '\t' -> pos++
					'\n' -> { pos++; lineNum++ }
					'#' -> skipLine()
					else -> parseLine()
				}
			}
		} catch(e: Exception) {
			System.err.println("Error on line $lineNum")
			e.printStackTrace()
			exitProcess(1)
		}
	}



	private fun add(enc: ParsedEnc) {
		val multiIndex = enc.ops.indexOfFirst { it.first != null }

		if(multiIndex != -1) {
			val multi = enc.ops[multiIndex]
			add(enc.copy(ops = enc.withOp(multiIndex, multi.first!!)))
			add(enc.copy(ops = enc.withOp(multiIndex, multi.second!!)))

		}
	}
	private fun parseLine() {
		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var opcode = 0
		var ext = -1
		var mask = 0
		var opsString = ""
		var rw = 0
		var o16 = 0
		var a32 = 0
		var opreg = false
		var mnemonic = ""
		var pseudo = -1
		var vexw = VexW.W0
		var vexl = VexL.L0
		var vex = false
		var mr = false

		if(chars[pos] == 'W') {
			vex = true
			pos++

			vexw = when(chars[pos++]) {
				'G' -> VexW.WIG
				'0' -> VexW.W0
				'1' -> VexW.W1
				else -> error("Invalid vex.w: ${chars[pos - 1]}")
			}

			skipSpaces()

			if(chars[pos++] != 'L') error("Expecting VEX.L")

			vexl = when(chars[pos++]) {
				'G' -> VexL.LIG
				'1' -> VexL.L1
				'0' -> VexL.L0
				'L' -> VexL.L0
				else -> error("Invalid vex.l: ${chars[pos - 1]}")
			}

			skipSpaces()
		}

		while(true) {
			if(chars[pos] == 'N' && chars[pos+1] == 'P') {
				pos += 2
				skipSpaces()
				continue
			}

			if(chars[pos + 2] != ' ' || !chars[pos].isHex || !chars[pos + 1].isHex)
				break

			val value = chars[pos].toHex shl 4 or chars[pos + 1].toHex

			if(chars[pos + 2] == '/') {
				ext = chars[pos + 3].toHex
				pos += 4
			} else
				pos += 2

			if(opcode == 0) {
				when(value) {
					0x66 -> if(escape == Escape.NONE) prefix = Prefix.P66 else opcode = 0x66
					0xF2 -> if(escape == Escape.NONE) prefix = Prefix.PF2 else opcode = 0xF2
					0xF3 -> if(escape == Escape.NONE) prefix = Prefix.PF3 else opcode = 0xF3
					0x9B -> if(escape == Escape.NONE) prefix = Prefix.P9B else opcode = 0x9B
					0x0F -> if(escape == Escape.NONE) escape = Escape.E0F else opcode = 0x0F
					0x38 -> if(escape == Escape.E0F || (vex && escape == Escape.NONE))
						escape = Escape.E38 else opcode = 0x38
					0x3A -> if(escape == Escape.E0F || (vex && escape == Escape.NONE))
						escape = Escape.E3A else opcode = 0x3A
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

		mnemonic = readPart()
		skipSpaces()

		while(chars[pos] != '\n' && chars[pos] != '\r') {
			val part = readPart()
			skipSpaces()

			when {
				part.isEmpty() ->
					error("Empty part")

				part[0] == ':'  ->
					pseudo = part.drop(1).toIntOrNull() ?: error("Invalid pseudo: $part")

				part[0] == '0' || part[0] == '1' ->
					mask = part.toIntOrNull(2) ?: error("Invalid mask: $part")

				part == "RW"    -> rw = 1
				part == "O16"   -> o16 = 1
				part == "A32"   -> a32 = 1
				part == "OPREG" -> opreg = true
				part == "MR"    -> mr = true
				else            -> opsString = part
			}
		}


	}


}