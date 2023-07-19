package eyre.gen

import eyre.Escape
import eyre.Mnemonic
import eyre.Prefix
import eyre.util.hexc8

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
	val avx      : Boolean,
	val evex     : Boolean
) {

	val simdOpEnc = SimdOpEnc.entries.firstOrNull { opEnc in it.encs }

	val memWidth = ops.firstOrNull { it.type.isMem }?.width

	val op1 = ops.getOrNull(0)
	val op2 = ops.getOrNull(1)
	val op3 = ops.getOrNull(2)
	val op4 = ops.getOrNull(3)

	val opsString = ops.joinToString("_")

	private val vsibValue = when(vsib) {
		null -> 0
		VSib.VM32X, VSib.VM64X, VSib.VSIBX -> 1
		VSib.VM32Y, VSib.VM64Y, VSib.VSIBY -> 2
		VSib.VSIBZ -> 3
	}

	val temp = Temp(TempOp.from(op1), TempOp.from(op2), TempOp.from(op3), TempOp.from(op4), memWidth, vsibValue)

	fun compactAvxString() = buildString {
		if(evex) append("E.") else append("V.")
		when(vexl) {
			VexL.LIG  -> { }
			VexL.L0   -> append("L0.")
			VexL.LZ   -> append("LZ.")
			VexL.L1   -> append("L1.")
			VexL.L128 -> if(Op.X !in ops || Op.Y in ops || Op.Z in ops) append("L128.")
			VexL.L256 -> if(Op.Y !in ops || Op.X in ops || Op.Z in ops) append("L256.")
			VexL.L512 -> if(Op.Z !in ops || Op.X in ops || Op.Y in ops) append("L512.")
		}
		append("${prefix.avxString}.${escape.avxString}")
		when(vexw) {
			VexW.WIG -> append(" ")
			VexW.W0  -> append(".W0 ")
			VexW.W1  -> append(".W1 ")
		}
		append(opcode.hexc8)
		if(hasExt) append("/$ext")
		append("  $mnemonic  ")
		append(opsString)
		append("  ")
		if(pseudo >= 0) append(":$pseudo  ")
		append("$opEnc  ")
		tuple?.let { append("$it ") }
		if(k) if(z) append("KZ ") else append("K ")
		if(sae) append("SAE ")
		if(er) append("ER ")
		when(bcst) {
			0 -> { }
			1 -> append("B16 ")
			2 -> append("B32 ")
			3 -> append("B64 ")
		}
		vsib?.let { append("$it ") }
	}

	override fun toString() = buildString {
		if(avx) {
			if(evex) append("E.") else append("V.")
			append("${vexl.name}.${prefix.avxString}.${escape.avxString}.${vexw.name} ${opcode.hexc8}")
			if(hasExt) append("/$ext")
			append("  $mnemonic  ")
			append(opsString)
			append("  ")
			if(pseudo >= 0) append(":$pseudo  ")
			append("$opEnc  ")
			tuple?.let { append("$it ") }
			if(k) if(z) append("KZ ") else append("K ")
			if(sae) append("SAE ")
			if(er) append("ER ")
			when(bcst) {
				0 -> { }
				1 -> append("B16 ")
				2 -> append("B32 ")
				3 -> append("B64 ")
			}
			vsib?.let { append("$it ") }
		} else {
			prefix.string?.let { append("$it ") }
			escape.string?.let { append("$it ") }
			if(opcode and 0xFF00 != 0) append("${(opcode shr 8).hexc8} ")
			append((opcode and 0xFF).hexc8)
			if(ext >= 0) append("/$ext")
			append("  $mnemonic  ")
			if(ops.isNotEmpty()) append(opsString) else append("NONE")
			append("  ")
			if(rw == 1) append("RW ")
			if(o16 == 1) append("O16 ")
			if(pseudo >= 0) append(":$pseudo ")
			if(mr) append("MR ")
		}
		trimEnd()
	}

}