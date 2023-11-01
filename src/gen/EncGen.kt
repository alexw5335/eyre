package eyre.gen

import eyre.*

object EncGen {


	val parser = ManualParser("res/encs.txt").also { it.parse() }

	val groups = parser.groups

	val zeroOperandOpcodes = genZeroOperandOpcodes()



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