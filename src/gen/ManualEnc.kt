package eyre.gen

import eyre.*



class ManualGroup {
	var isCompact = false
	var ops = 0
	val encs = ArrayList<ManualEnc>()
}



data class ManualEnc(
	val mnemonic: String,
	val prefix: Prefix,
	val escape: Escape,
	val opcode: Int,
	val ext: Int,
	val mask: Int,
	val compactOps: CompactOps,
	val rw: Int,
	val o16: Int,
	val a32: Int,
	val opreg: Boolean,
	val ops: List<ManualOp>,
	val pseudo: Int,
	val vex: Boolean,
	val vexw: VexW,
	val vexl: VexL,
) {
	val isAmbiguous = ops.any { it.isAmbiguous }
	val isCompact get() = compactOps != CompactOps.NONE
	val opcode1 = opcode and 0xFF
	val opcode2 = opcode shr 8
	val hasExt get() = ext >= 0
}