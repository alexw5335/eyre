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
	val mnemonic : Mnemonic,
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
	val modrm    : Boolean,
	val nd       : Boolean,
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

	val isAvx = NasmExt.AVX in exts || NasmExt.AVX2 in exts || NasmExt.FMA in exts
	// All encodings with AVX are either [AVX] or [AVX, GFNI]
	val avxOnly = exts.size == 1 && exts[0] == NasmExt.AVX
	// All encodings with AVX2 have AVX2 as their only extension
	val avx2 = exts.size == 1 && exts[0] == NasmExt.AVX2
	val avxOrAvx2 = exts.size == 1 && (exts[0] == NasmExt.AVX || exts[0] == NasmExt.AVX2)

	val opCount = ops.size
	val op1 = ops.getOrElse(0) { NasmOp.NONE }
	val op2 = ops.getOrElse(1) { NasmOp.NONE }
	val op3 = ops.getOrElse(2) { NasmOp.NONE }
	val op4 = ops.getOrElse(3) { NasmOp.NONE }
	val opsValue = (op1.ordinal shl 24) or (op2.ordinal shl 16) or (op3.ordinal shl 8) or op4.ordinal

	val opsString = ops.joinToString("_")
	val children = ArrayList<NasmEnc>()
	val opcode1 = opcode and 0xFF
	val opcode2 = opcode shr 8
	val p66 = if(prefix == Prefix.P66) 1 else 0
	val p9B = if(prefix == Prefix.P9B) 1 else 0
	val pF2 = if(prefix == Prefix.PF2) 1 else 0
	val pF3 = if(prefix == Prefix.PF3) 1 else 0

	val prefixes = (p66 or o16) or (a32 shl 1) or (p9B shl 3) or (pF2 shl 4) or (pF3 shl 5)

	val immWidth get() = when(ops.lastOrNull()) {
		NasmOp.I8    -> Width.BYTE
		NasmOp.I16   -> Width.WORD
		NasmOp.I32   -> Width.DWORD
		NasmOp.I64   -> Width.QWORD
		NasmOp.REL8  -> Width.BYTE
		NasmOp.REL16 -> Width.WORD
		NasmOp.REL32 -> Width.DWORD
		else         -> null
	}

	override fun hashCode() = System.identityHashCode(this)

	override fun equals(other: Any?) = other === this

	override fun toString() = buildString {
		@Suppress("UnusedReceiverParameter")
		fun Any.comma() = append(", ")
		append("NasmEnc(")
		append(mnemonic).comma()
		append(prefix).comma()
		append(escape).comma()
		append(opcode.hexc).comma()
		if(hasExt) { append('/'); append(ext).comma() }
		append(opEnc).comma()
		append(if(ops.isEmpty()) "NONE" else ops.joinToString("_")).comma()
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