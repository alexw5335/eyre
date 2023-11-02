package eyre.gen

import eyre.*

object EncGen {


	val parser = ManualParser("res/encs.txt").also { it.parse() }

	val groups = parser.groups

	val zeroOperandOpcodes = genZeroOperandOpcodes()

	val autoEncs = genAutoEncs()



	/*
	Encoding generation
	 */



	fun printAutoEncs() {
		println("val autoEncs = arrayOf<LongArray>(")
		for(mnemonic in Mnemonic.entries) {
			val encs = autoEncs[mnemonic.ordinal]
			if(encs.isEmpty()) {
				println("\tLongArray(0),")
				continue
			}
			print("\tlongArrayOf(")
			for((i, enc) in encs.withIndex())
				print("${enc}L${if(i != encs.lastIndex) ", " else ""}")
			println("), ")
		}
		println(")")
	}



	private fun genAutoEncs(): Array<LongArray> {
		val empty = LongArray(0)
		val array = Array<LongArray>(Mnemonic.entries.size) { empty }
		for(mnemonic in Mnemonic.entries) {
			val group = groups[mnemonic] ?: continue
			val encs = group.encs.map(::genAutoEnc).filter(AutoEnc::isNotNull)
			if(encs.isEmpty()) continue
			array[mnemonic.ordinal] = LongArray(encs.size) { encs[it].value }
		}
		return array
	}



	private fun genAutoEnc(enc: ParsedEnc): AutoEnc {
		if(enc.ops.isEmpty() || enc.prefix == Prefix.P9B)
			return AutoEnc()

		var width = 0
		var vsib = 0

		for(op in enc.ops) when(op) {
			Op.M8    -> width = 1
			Op.M16   -> width = 2
			Op.M32   -> width = 3
			Op.M64   -> width = 4
			Op.M80   -> width = 5
			Op.M128  -> width = 6
			Op.M256  -> width = 7
			Op.M512  -> width = 8
			Op.VM32X -> { width = 3; vsib = 1 }
			Op.VM64X -> { width = 4; vsib = 1 }
			Op.VM32Y -> { width = 3; vsib = 2 }
			Op.VM64Y -> { width = 4; vsib = 2 }
			Op.VM32Z -> { width = 3; vsib = 3 }
			Op.VM64Z -> { width = 4; vsib = 3 }
			else     -> continue
		}

		val autoOps = AutoOps(
			enc.op1.type.ordinal,
			enc.op2.type.ordinal,
			enc.op3.type.ordinal,
			enc.op4.type.ordinal,
			width,
			vsib,
			if(enc.op2 == Op.ST0) 1 else 0
		)

		return AutoEnc(
			enc.opcode,
			enc.prefix.ordinal,
			enc.escape.ordinal,
			if(enc.ext == -1) 0 else enc.ext,
			enc.rw,
			enc.o16,
			enc.a32,
			enc.opEnc.ordinal,
			if(enc.pseudo == -1) 0 else enc.pseudo + 1,
			if(enc.vex) 1 else 0,
			enc.vexw.value,
			enc.vexl.value,
			autoOps.value,
		)
	}



	private fun genZeroOperandOpcodes(): IntArray {
		val values = IntArray(Mnemonic.entries.size)
		for(enc in parser.encs) {
			if(enc.ops.isNotEmpty()) continue
			var value = if(enc.opcode and 0xFF00 != 0)
				(enc.opcode1) or (enc.opcode2 shl 8)
			else
				enc.opcode1
			value = when(enc.escape) {
				Escape.NONE -> value
				Escape.E0F  -> (value shl 8) or 0x0F
				Escape.E38  -> (value shl 16) or 0x380F
				Escape.E3A  -> (value shl 16) or 0x3A0F
			}
			if(enc.prefix != Prefix.NONE)
				value = (value shl 8) or enc.prefix.value
			if(enc.o16 == 1)
				value = (value shl 8) or 0x66
			if(enc.rw == 1)
				value = (value shl 8) or 0x48
			if(enc.a32 == 1)
				value = (value shl 8) or 0x67
			values[enc.mnemonic.ordinal] = value
		}
		return values
	}


}