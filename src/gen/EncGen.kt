package eyre.gen

import eyre.AutoEnc
import eyre.OpType
import eyre.int

fun main() = EncGen.main()

object EncGen {


	private fun readManualEncs() = ManualParser("res/encs.txt").parseAndConvert()

	private fun readNasmEncs() = NasmParser("res/nasm.txt").parseAndConvert()



	fun main() {
		genAutoEncs()
	}



	private fun genAutoEncs() {
		val encs = readNasmEncs()

		for(enc in encs) {
			if(enc.avx || enc.evex) continue
			if(enc.opcode shr 16 != 0) continue
			if(enc.mnemonic == "MOV") continue
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
				type(0),
				type(1),
				type(2),
				type(3),
				width,
				vsib,
			)
		}
	}



	private fun genMnemonics() {
		val encs = readNasmEncs()
		val unsorted = HashSet<String>()
		for(e in encs) unsorted += e.mnemonic
		val sorted = unsorted.sortedBy { it }
		println("DLLCALL,")
		println("RETURN,")
		for(i in sorted.indices) {
			print("\t${sorted[i]}")
			println(if(i == sorted.lastIndex) ';' else ',')
		}
	}


}