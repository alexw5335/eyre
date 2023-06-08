package eyre.manual

import eyre.*
import eyre.nasm.Maps
import java.nio.file.Files
import java.nio.file.Paths
import eyre.Op.*
import eyre.util.isHex

class ManualParser(private val inputs: List<String>) {


	constructor(path: String) : this(Files.readAllLines(Paths.get(path)))



	val lines = ArrayList<ManualLine>()

	val encodings = ArrayList<Encoding>()



	fun read() {
		for(i in inputs.indices) readLine(i, inputs[i], lines)
		for(l in lines) convertLine(l, encodings)
	}



	private fun readLine(index: Int, input: String, list: ArrayList<ManualLine>) {
		if(input.isEmpty() || input.startsWith(';')) return

		val parts = input.split(' ').filter { it.isNotEmpty() }

		var pos = 0

		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var opcode = 0
		var ext = 0
		var mask = OpMask(0)
		var opsString = "NONE"
		var rexw = false
		var o16 = false
		val mnemonic: String
		var norexw = false
		var pseudo = -1

		while(true) {
			var part = parts[pos++]

			if('/' in part) {
				ext = part.last().digitToInt()
				part = part.substring(0..1)
			} else if(part.length > 2 || !part[0].isHex || !part[1].isHex) {
				mnemonic = part
				break
			}

			val value = part.toInt(16)

			if(opcode == 0) {
				when(value) {
					0x66 -> if(escape == Escape.NONE) prefix = Prefix.P66 else opcode = 0x66
					0xF2 -> if(escape == Escape.NONE) prefix = Prefix.PF2 else opcode = 0xF2
					0xF3 -> if(escape == Escape.NONE) prefix = Prefix.PF3 else opcode = 0xF3
					0x9B -> if(escape == Escape.NONE) prefix = Prefix.P9B else opcode = 0x9B
					0x67 -> if(escape == Escape.NONE) prefix = Prefix.P67 else opcode = 0x67
					0x0F -> if(escape == Escape.NONE) escape = Escape.E0F else opcode = 0x0F
					0x38 -> if(escape == Escape.E0F) escape = Escape.E38 else opcode = 0x38
					0x3A -> if(escape == Escape.E0F) escape = Escape.E3A else opcode = 0x3A
					0x00 -> if(escape == Escape.E0F) escape = Escape.E00 else opcode = 0x00
					else -> opcode = value
				}
			} else {
				opcode = opcode or (value shl 8)
			}
		}

		if(mnemonic == "WAIT") {
			opcode = 0x9B
			prefix = Prefix.NONE
		}

		if(pos < parts.size && parts[pos] != "RW" && parts[pos] != "O16")
			opsString = parts[pos++]

		while(pos < parts.size) {
			when(val part = parts[pos++]) {
				"RW"   -> rexw = true
				"O16"  -> o16 = true
				"NORW" -> norexw = true
				else   ->
					if(part.startsWith(":"))
						pseudo = part.drop(1).toInt()
					else
						mask = OpMask(part.toIntOrNull(2) ?: error("Line ${index + 1}: Invalid part: $part"))
			}
		}

		if(opsString == "X_XM_X0")
			opsString = "X_XM"

		val ops = ArrayList(opsString.split('_'))

		var multi: Pair<String, String>? = null
		var multiIndex = -1

		fun add(mnemonic: String, opcode: Int, opsString: String, prefix: Prefix) = list.add(ManualLine(
			index,
			prefix,
			escape,
			ext,
			opcode,
			mnemonic,
			opsString,
			mask,
			if(rexw) 1 else 0,
			if(o16) 1 else 0,
			norexw,
			pseudo
		))

		fun add(mnemonic: String, opcode: Int) {
			if(multi != null) {
				ops[multiIndex] = multi!!.first
				add(mnemonic, opcode, ops.joinToString("_"), prefix)
				ops[multiIndex] = multi!!.second
				add(mnemonic, opcode, ops.joinToString("_"), prefix)
			} else {
				add(mnemonic, opcode, opsString, prefix)
			}
		}

		for((i, op) in ops.withIndex()) {
			multiOpMap[op]?.let {
				if(multiIndex >= 0) error("Too many multi-ops")
				multiIndex = i
				multi = it
			}
		}

		if(opsString == "E_EM") {
			add(mnemonic, opcode, "MM_MM", prefix)
			add(mnemonic, opcode, "MM_M", prefix)
			add(mnemonic, opcode, "X_X", Prefix.P66)
			add(mnemonic, opcode, "X_M", Prefix.P66)
		} else if(opsString == "E_I8") {
			add(mnemonic, opcode, "MM_I8", prefix)
			add(mnemonic, opcode, "X_I8", Prefix.P66)
		} else if(opsString == "E_EM_I8") {
			add(mnemonic, opcode, "MM_MM_I8", prefix)
			add(mnemonic, opcode, "MM_M_I8", prefix)
			add(mnemonic, opcode, "X_X_I8", Prefix.P66)
			add(mnemonic, opcode, "X_M_I8", Prefix.P66)
		} else if(mnemonic.endsWith("cc")) {
			for((postfix, opcodeInc) in Maps.ccList)
				add(mnemonic.dropLast(2) + postfix, opcode + opcodeInc)
		} else {
			add(mnemonic, opcode)
		}
	}



	private val opMap = Op.values().associateBy { it.name }

	private val multiOpMap = mapOf(
		"RM" to Pair("R", "M"),
		"RM8" to Pair("R8", "M8"),
		"RM16" to Pair("R16", "M16"),
		"RM32" to Pair("R32", "M32"),
		"RM64" to Pair("R64", "M64"),
		"XM" to Pair("X", "M128"),
		"MMM" to Pair("MM", "M64"),
		"XM16" to Pair("X", "M16"),
		"XM32" to Pair("X", "M32"),
		"XM64" to Pair("X", "M64"),
		"BNDM128" to Pair("BND", "M128")
	)



	private fun Width?.map(vararg ops: Op) = ops[this?.ordinal ?: error("No width for ops: $ops")]

	private fun convert(op: String, width: Width?): Op = when(op) {
		in opMap -> opMap[op]!!
		"A"     -> width.map(AL, AX, EAX, RAX)
		"I"     -> width.map(I8, I16, I32, I32)
		"O",
		"RA",
		"R"     -> width.map(R8, R16, R32, R64)
		"M"     -> width?.map(M8, M16, M32, M64, M128, M256, M512) ?: MEM
		"MOFFS" -> width.map(MOFFS8, MOFFS16, MOFFS32, MOFFS64)
		"1"     -> ONE
		"MIB"   -> MEM
		else -> error("Invalid ops: $op $width")
	}



	private fun convertLine(line: ManualLine, list: ArrayList<Encoding>) {
		fun add(ops: List<Op>, rexw: Int, o16: Int, opcode: Int) {
			list += Encoding(
				line.mnemonic,
				line.prefix,
				line.escape,
				opcode,
				line.ext,
				ops,
				rexw,
				o16,
				line.pseudo
			)
		}

		fun add(vararg ops: Op) {
			list += Encoding(
				line.mnemonic,
				line.prefix,
				line.escape,
				line.opcode,
				line.ext,
				ops.toList(),
				line.rexw,
				line.o16,
				line.pseudo
			)
		}

		if(line.mask.isEmpty) {
			when(line.opsString) {
				"NONE" -> { add(emptyList(), line.rexw, line.o16, line.opcode); return }
				"X_M"  -> { add(X, M128); return }
				"M_X"  -> { add(M128, X); return }
				"MM_M" -> { add(MM, M64); return }
				"M_MM" -> { add(M64, MM); return }
			}
		}

		val ops = line.opsString.split('_')

		if(line.mask.isEmpty)
			add(ops.map { convert(it, null) }, line.rexw, line.o16, line.opcode)
		else
			line.mask.forEachWidth { width ->
				val rexw = if(line.rexw == 1)
					1
				else if(width == Width.QWORD && !line.norexw && Width.DWORD in line.mask && "RA" !in line.opsString)
					1
				else
					0

				val o16 = if(line.o16 == 1)
					1
				else if(width == Width.WORD && line.mask != OpMask.WORD)
					1
				else
					0

				val opcode = if(width == Width.BYTE)
					line.opcode
				else if(line.mask != OpMask.BYTE && Width.BYTE in line.mask)
					line.opcode + 1
				else
					line.opcode

				add(ops.map { convert(it, width) }, rexw, o16, opcode)
			}
	}



}