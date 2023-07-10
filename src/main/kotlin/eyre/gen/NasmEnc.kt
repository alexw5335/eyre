package eyre.gen

import eyre.Escape
import eyre.Prefix
import eyre.util.hexc8

/**
 *     O16/MR/RW: GP/SSE
 *     PSEUDO: SSE/AVX
 *     VEX.L/VEX/VEX.W: AVX/AVX512
 *     SAE/ER/BCST/VSIB/EVEX/TUPLE/K/Z: AVX512
 */
class NasmEnc2(
	val mnemonic : String,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val ext      : Int,
	val exts     : List<NasmExt>,
	val opEnc    : OpEnc,
	val ops      : List<Op>,
	val rw       : Int,
	val o16      : Int,
	val pseudo   : Int,
	val mr       : Boolean,
	val vexw     : VexW,
	val vexl     : VexL,
	val tuple    : TupleType?,
	val sae      : Boolean,
	val er       : Boolean,
	val bcst     : Int,
	val vsib     : VSib?,
	val k        : Boolean,
	val z        : Boolean,
	val vex      : Boolean,
	val evex     : Boolean
) {

	override fun toString() = buildString {
		if(vex || evex) {
			if(vex) append("V.") else append("E.")
			append("${vexl.name}.${prefix.avxString}.${escape.avxString}.${vexw.name} ${opcode.hexc8}  $mnemonic  ")
			if(k) if(z) append("{KZ}  ") else append("{k}  ")
			append(ops.joinToString("_"))
			append("  $opEnc  ")
			tuple?.let { append("$it ") }
			if(sae) append("SAE ")
			if(er) append("ER ")
			when(bcst) {
				0 -> { }
				1 -> append("BCST16 ")
				2 -> append("BCST32 ")
				3 -> append("BCST64 ")
			}
			vsib?.let { append("$it ") }
			append(" ${exts.joinToString()}")
			return@buildString
		}

		prefix.string?.let { append("$it ") }
		escape.string?.let { append("$it ") }
		if(opcode and 0xFF00 != 0) append("${(opcode shr 8).hexc8} ")
		append((opcode and 0xFF).hexc8)
		if(ext >= 0) append("/$ext")
		append("  $mnemonic  ")
		if(ops.isNotEmpty()) append(ops.joinToString("_")) else append("NONE")
		append("  ")
		if(rw == 1) append("RW ")
		if(o16 == 1) append("O16 ")
		if(pseudo >= 0) append(":$pseudo ")
		if(mr) append("MR ")
	}
}



class NasmEnc(
	val mnemonic : String,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val ext      : Int,
	val ops      : List<Op>,
	val rexw     : Int,
	val o16      : Int,
	val pseudo   : Int,
	val mr       : Boolean,
	val isAvx    : Boolean,
	val exts     : List<NasmExt>,
	val opEnc    : OpEnc,
) {

	override fun toString() = buildString {
		prefix.string?.let { append("$it ") }
		escape.string?.let { append("$it ") }
		if(opcode and 0xFF00 != 0) append("${(opcode shr 8).hexc8} ")
		append((opcode and 0xFF).hexc8)
		if(ext >= 0) append("/$ext")
		append("  $mnemonic  ")
		if(ops.isNotEmpty()) append(ops.joinToString("_")) else append("NONE")
		append("  ")
		if(rexw == 1) append("RW ")
		if(o16 == 1) append("O16 ")
		if(pseudo >= 0) append(":$pseudo ")
		if(mr) append("MR ")
	}

}