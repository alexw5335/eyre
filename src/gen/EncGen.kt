package eyre.gen

import eyre.*



fun main() = EncGen.main()



class DisasmGroup(val opcode: Int) {
	var ext = false
	var modrm = false
	val encs = ArrayList<NasmEnc>()
}



object EncGen {


	val manualParser = ManualParser("res/encs.txt").also { it.parseAndConvert() }
	val manualEncs   = manualParser.encs
	val nasmParser   = NasmParser("res/nasm.txt").also { it.parseAndConvert() }
	val encs         = nasmParser.encs
	val expandedEncs = nasmParser.allEncs
	val groups       = nasmParser.groups
	val disasmGroups = createDisasmGroups()



	fun main() {
		test()
	}



	private fun test() {
		var prev = Mnemonic.NONE.name

		class Combo(val x: String, val y: String, val s: String)

		val combos = listOf(
			Combo("X_X_XM128", "Y_Y_YM256", "S_S_SM"),
			Combo("X_X_M128", "Y_Y_M256", "S_S_M"),
			Combo("M128_X_X", "M256_Y_Y", "M_S_S"),
			Combo("X_XM128_I8", "Y_YM256_I8", "S_SM_I8"),
			Combo("X_X_XM128_X", "Y_Y_YM256_Y", "S_S_SM_S"),
			Combo("X_XM128", "Y_YM256", "S_SM"),
			Combo("XM128_X", "YM256_Y", "SM_S"),
			Combo("M128_X", "M256_Y", "M_S"),
			Combo("X_M128", "Y_M256", "S_M"),
			Combo("R32_X", "R32_Y", "R32_S"),
			Combo("R64_X", "R64_Y", "R64_S"),
			Combo("X_X_XM128_I8", "Y_Y_YM256_I8", "S_S_SM_I8")
		)

		for(group in groups) {
			val ignored = ArrayList<NasmEnc>()
			var first = true

			for(enc in group.encs) {
				if(enc.ops.isEmpty()) continue
				if(!enc.isAvx) continue
				if(enc.rw == 1 || enc.a32 == 1 || enc.o16 == 1) error("Unexpected")
				if(enc in ignored) continue
				if(enc.pseudo == -1) continue

				if(first) {
					first = false
					val next = group.mnemonic.name

					var newline = true
					if(prev.length == next.length && next.length >= 3) {
						var valid = true
						for(i in 0 ..< next.length - 2)
							if(prev[i] != next[i])
								valid = false

						if(valid) {
							newline = false
						}
					}

					if(newline) println()
					prev = group.mnemonic.name
				}

				var opsString = enc.opsString
				var ambiguousL = false
				for(combo in combos) {
					if(enc.opsString == combo.x) {
						group.encs.firstOrNull { it.isAvx && it.opsString == combo.y }?.let {
							if (enc.prefix != it.prefix ||
								enc.opcode != it.opcode ||
								enc.vexw != it.vexw ||
								enc.vexl.value != 0 ||
								it.vexl.value != 1
							)
								error("Invalid combo: $enc  --  $it")
							ignored.add(it)
							opsString = combo.s
							ambiguousL = true
						}
					}
				}
				when(enc.vexw) {
					NasmVexW.W0  -> print("W0 ")
					NasmVexW.W1  -> print("W1 ")
					NasmVexW.WIG -> print("WG ")
				}
				if(ambiguousL)
					print("LL ")
				else when(enc.vexl) {
					NasmVexL.LIG  -> print("LG ")
					NasmVexL.L0   -> print("L0 ")
					NasmVexL.LZ   -> print("L0 ")
					NasmVexL.L1   -> print("L1 ")
					NasmVexL.L128 -> print("L0 ")
					NasmVexL.L256 -> print("L1 ")
					NasmVexL.L512 -> print("L2 ")
				}

				print(enc.prefix.avxString)
				print(' ')
				print(enc.opcode.hexc8)
				print("  ")
				print(enc.mnemonic)
				print("  ")
				print(opsString)
				if(enc.pseudo != -1) print("  :${enc.pseudo}")
				println()
			}
		}
	}



	fun createDisasmGroups(): List<List<DisasmGroup>> = Escape.entries.map { escape ->
		val groups = List(256, ::DisasmGroup)
		for(enc in expandedEncs) {
			if(enc.nd || enc.avx || enc.escape != escape || enc.opcode2 != 0) continue
			val group = groups[enc.opcode1]
			if(group.encs.isNotEmpty()) {
				if(group.modrm != enc.modrm) error("Invalid modrm: $enc")
				if(group.ext != enc.hasExt) System.err.println("Invalid ext: $enc")
			} else {
				group.modrm = enc.modrm
				group.ext = enc.hasExt
			}
			group.modrm = enc.modrm
			group.encs.add(enc)
		}
		groups
	}



	private fun genAutoEncs() {
		val map = HashMap<Mnemonic, ArrayList<AutoEnc>>()

		for(enc in expandedEncs) {
			if(enc.avx || enc.evex) continue
			if(enc.opcode shr 16 != 0) continue
			if(enc.mnemonic == Mnemonic.MOV) continue
			if(enc.ops.any { !NasmLists.opTypeConversionMap.contains(it) }) continue

			val opEnc = NasmLists.opEncConversionMap[enc.opEnc] ?: continue

			fun type(index: Int): Int {
				if(index >= enc.ops.size) return OpType.NONE.ordinal
				val op = enc.ops[index]
				val type = NasmLists.opTypeConversionMap[op] ?: error("Invalid op: $op -- $enc")
				return type.ordinal
			}

			var width = 0
			var vsib = 0

			for(op in enc.ops) when(op) {
				NasmOp.M8 -> width = 1
				NasmOp.M16 -> width = 2
				NasmOp.M32 -> width = 3
				NasmOp.M64 -> width = 4
				NasmOp.M80 -> width = 5
				NasmOp.M128 -> width = 6
				NasmOp.M256 -> width = 7
				NasmOp.M512 -> width = 8
				NasmOp.VM32X -> { width = 3; vsib = 1 }
				NasmOp.VM64X -> { width = 4; vsib = 1 }
				NasmOp.VM32Y -> { width = 3; vsib = 2 }
				NasmOp.VM64Y -> { width = 4; vsib = 2 }
				NasmOp.VM32Z -> { width = 3; vsib = 3 }
				NasmOp.VM64Z -> { width = 4; vsib = 3 }
				else -> continue
			}

			val auto = AutoEnc(
				enc.opcode,
				enc.prefix.avxValue,
				enc.escape.avxValue,
				enc.ext.coerceAtLeast(0),
				enc.rw,
				enc.o16,
				enc.a32,
				enc.opreg.int,
				opEnc.ordinal,
				AutoOps(
					type(0),
					type(1),
					type(2),
					type(3),
					width,
					vsib
				).value
			)

			map.getOrPut(enc.mnemonic, ::ArrayList).add(auto)
		}

		println("val autoEncs = mapOf<Mnemonic, LongArray>(")
		for((mnemonic, encs) in map) {
			print("\tMnemonic.$mnemonic to longArrayOf(")
			for(e in encs) {
				print(e.value)
				print(", ")
			}
			println("),")
		}
		println(")")
	}



/*	private fun genMnemonics() {
		val unsorted = HashSet<String>()
		for(e in encs) unsorted += e.mnemonic
		val sorted = unsorted.sortedBy { it }
		println("DLLCALL,")
		println("RETURN,")
		for(i in sorted.indices) {
			print("\t${sorted[i]}")
			println(if(i == sorted.lastIndex) ';' else ',')
		}
	}*/


}