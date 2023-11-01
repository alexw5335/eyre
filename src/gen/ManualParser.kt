package eyre.gen

import eyre.*
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess



class ManualParser(private val lines: List<String>) {

	constructor(path: String) : this(Files.readAllLines(Paths.get(path)))

	val encs = ArrayList<ParsedEnc>()

	val groups = LinkedHashMap<Mnemonic, EncGroup>()



	/*
	Public functions
	 */



	fun parse() {
		for(i in lines.indices) {
			try {
				parseLine(lines[i])
			} catch(e: Exception) {
				System.err.println("Error on line ${i+1}: ${lines[i]}")
				e.printStackTrace()
				exitProcess(1)
			}
		}
	}



	private fun add(enc: ParsedEnc) {
		val multiIndex = enc.ops.indexOfFirst { it.first != null }

		if(multiIndex != -1) {
			val multi = enc.ops[multiIndex]
			add(enc.copy(ops = enc.withOp(multiIndex, multi.first!!)))
			add(enc.copy(ops = enc.withOp(multiIndex, multi.second!!)))
			return
		}

		if(enc.ops.any { it.widths != null }) {
			fun ops(index: Int) = enc.ops.map { op ->
				op.widths?.get(index)?.let {
					if(index == 3 && op == Op.I && enc.opreg)
						Op.I64
					else
						it
				} ?: op
			}

			val o16 = if(enc.mask == 2) 0 else 1
			val opcode = enc.opcode + if(enc.mask and 1 == 1) 1 else 0
			val rw = if(enc.mask and 4 == 4) 1 else 0

			if(enc.mask and 1 == 1)
				add(enc.copy(ops = ops(0)))
			if(enc.mask and 2 == 2)
				add(enc.copy(ops = ops(1), opcode = opcode, o16 = o16))
			if(enc.mask and 4 == 4)
				add(enc.copy(ops = ops(2), opcode = opcode))
			if(enc.mask and 8 == 8)
				add(enc.copy(ops = ops(3), opcode = opcode, rw = rw))

			return
		}

		val group = groups.getOrPut(enc.mnemonic) { EncGroup(enc.mnemonic) }
		group.encs.add(enc)
		encs.add(enc)
	}



	private fun parseLine(line: String) {
		if(line.isEmpty() || line[0] == '#') return

		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var opcode = 0
		var ext = -1
		var mask = 0
		var ops = ""
		var rw = 0
		var o16 = 0
		var a32 = 0
		var opreg = false
		var mnemonic = ""
		var pseudo = -1
		var vexw = VexW.W0
		var vexl = VexL.L0
		val vex = line.startsWith("W")

		for(part in line.split(' ').filter(String::isNotEmpty)) {
			if(part.isEmpty()) error("Empty part: '$part'")
			val isExt = part.length == 4 && part[2] == '/'

			when {
				part == "WG"    -> vexw = VexW.WIG
				part == "W0"    -> vexw = VexW.W0
				part == "W1"    -> vexw = VexW.W1
				part == "LL"    -> vexl = VexL.L0
				part == "LG"    -> vexl = VexL.LIG
				part == "L0"    -> vexl = VexL.L0
				part == "L1"    -> vexl = VexL.L1
				part == "RW"    -> rw = 1
				part == "O16"   -> o16 = 1
				part == "A32"   -> a32 = 1
				part == "NP"    -> prefix = Prefix.NONE
				part == "OPREG" -> opreg = true
				part == "I64"   -> { }

				part[0] == ':' ->
					pseudo = part.drop(1).toInt()

				mnemonic.isEmpty() && !isExt && (!part[0].isHex || !part[1].isHex || part.length > 2 ) ->
					mnemonic = part

				mnemonic.isNotEmpty() && (part[0] == '0' || part[0] == '1') ->
					mask = part.toInt(2)

				mnemonic.isNotEmpty() ->
					ops = part

				else -> {
					if(isExt)
						ext = part[3].digitToInt()
					else if(part.length != 2)
						error("Invalid part: $part")

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
				}
			}
		}

		if(prefix == Prefix.P9B && opcode == 0) {
			opcode = 0x9B
			prefix = Prefix.NONE
		}

		fun add(mnemonic: String, opcode: Int, ops: String, prefix: Prefix, vexl: VexL) = ParsedEnc(
			GenLists.mnemonics[mnemonic] ?: error("Missing mnemonic: $mnemonic"),
			prefix,
			escape,
			opcode,
			ext,
			mask,
			rw,
			o16,
			a32,
			opreg,
			if(ops == "")
				emptyList()
			else
				ops.split('_').map { Op.map[it] ?: error("Missing ops: $it") },
			pseudo,
			vex,
			vexw,
			vexl
		).also(this::add)

		if(ops in SimdCombo.map) {
			val combo = SimdCombo.map[ops]!!
			val firstVexL = if(combo.isAvx) VexL.L0 else vexl
			val secondVexL = if(combo.isAvx) VexL.L1 else vexl
			val secondPrefix = if(combo.isSse) Prefix.P66 else prefix
			add(mnemonic, opcode, combo.first, prefix, firstVexL)
			add(mnemonic, opcode, combo.second, secondPrefix, secondVexL)
		} else if(mnemonic.endsWith("cc")) {
			for((postfix, opcodeInc) in GenLists.ccList)
				add(mnemonic.dropLast(2) + postfix, opcode + opcodeInc, ops, prefix, vexl)
		} else {
			add(mnemonic, opcode, ops, prefix, vexl)
		}
	}


}