package eyre

import java.nio.file.Files
import java.nio.file.Paths

class EncParser(private val path: String) {


	private val opsMap = Ops.entries.associateBy { it.name }

	private val mnemonicsMap = Mnemonic.entries.associateBy { it.name }



	fun parse() {
		Files.readAllLines(Paths.get(path)).forEach(::parseLine)
	}



	private fun parseLine(line: String) {
		val parts = line.split(' ').filter { it.isNotEmpty() }

		var ext = 0
		var opcode = 0
		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var mask = 0
		var rw = 0
		var o16 = 0
		var opreg = false
		var ops: Ops = Ops.NONE
		var mnemonicString: String? = null

		fun addOpcode(part: Int) {
			when(opcode) {
				0 -> when(part) {
					0x66 -> prefix = Prefix.P66
					0xF2 -> prefix = Prefix.PF2
					0xF3 -> prefix = Prefix.PF3
					0x0F -> escape = Escape.E0F
					else -> opcode = part
				}
				0x0F -> when(part) {
					0x38 -> escape = Escape.E38
					0x3A -> escape = Escape.E3A
					else -> opcode = part
				}
				else -> opcode = part
			}
		}

		for(part in parts) {
			if(part.length == 4 && part[2] == '/') {
				ext = part[3].digitToInt()
				addOpcode(part.dropLast(2).toInt(16))
			} else if(part.length == 2 && part[0].isHex && part[1].isHex) {
				addOpcode(part.toInt(16))
			} else if(mnemonicString == null) {
				mnemonicString = part
			} else if(part.length == 4 && part[0] == '0' || part[0] == '1') {
				mask = part.toInt(2)
			} else when(part) {
				in opsMap -> ops = opsMap[part]!!
				"RW"      -> rw = 1
				"O16"     -> o16 = 1
				"OPREG"   -> opreg = true
				else      -> error("Invalid part: $part")
			}
		}

		val mnemonic = mnemonicsMap[mnemonicString]
		if(mnemonic == null)
			println("$mnemonicString,")
	}

}