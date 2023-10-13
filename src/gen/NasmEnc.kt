package eyre.gen

import eyre.*

/**
 *     O16/MR/RW: GP/SSE
 *     PSEUDO: SSE/AVX
 *     VEX.L/VEX/VEX.W: AVX/AVX512
 *     SAE/ER/BCST/VSIB/EVEX/TUPLE/K/Z: AVX512
 */
data class NasmEnc(
	val parent   : NasmEnc?,
	val mnemonic : String,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val ext      : Int,
	val hasExt   : Boolean,
	val exts     : List<NasmExt>,
	val opEnc    : NasmOpEnc,
	val ops      : List<NasmOp>,
	val rw       : Int,
	val o16      : Int,
	val a32      : Int,
	val opreg    : Boolean,
	val pseudo   : Int,
	val mr       : Boolean,
	val vexw     : NasmVexW,
	val vexl     : NasmVexL,
	val tuple    : NasmTuple?,
	val sae      : Boolean,
	val er       : Boolean,
	val bcst     : Int,
	val k        : Boolean,
	val z        : Boolean,
	val avx      : Boolean,
	val evex     : Boolean
) {

	override fun toString() = buildString {
		@Suppress("UnusedReceiverParameter")
		fun Any.comma() = append(", ")
		append("NasmEnc(")
		append(mnemonic).comma()
		append(prefix).comma()
		append(escape).comma()
		append(opcode.hexc).comma()
		append('/'); append(ext).comma()
		append(opEnc).comma()
		append(ops.joinToString("_")).comma()
		append("RW="); append(rw).comma()
		append("O16="); append(o16).comma()
		append("A32="); append(a32).comma()
		if(!avx) { delete(length - 2, length); append(')'); return@buildString }
		append(vexw).comma()
		append(vexl).comma()
		if(!evex) { delete(length - 2, length); append(')'); return@buildString }
		append(tuple).comma()
		append("SAE="); append(sae).comma()
		append("ER="); append(er).comma()
		append("BCST="); append(bcst).comma()
		append("K="); append(k).comma()
		append("Z="); append(z).comma()
		delete(length - 2, length)
		append(')')
	}

}