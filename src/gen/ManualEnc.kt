package eyre.gen

import eyre.*



class ManualGroup(val mnemonic: Mnemonic) {
	var ops = 0
	var isCompact = false
	val encs = ArrayList<ManualEnc>()
}



data class ManualEnc(
	val mnemonic: Mnemonic,
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
	val op1 = ops.getOrElse(0) { ManualOp.NONE }
	val op2 = ops.getOrElse(1) { ManualOp.NONE }
	val op3 = ops.getOrElse(2) { ManualOp.NONE }
	val op4 = ops.getOrElse(3) { ManualOp.NONE }

	val opEnc: OpEnc = when {
		op1.type.isMem && op2.type.isReg && op3.type.isReg -> OpEnc.MVR
		op1.type.isMem && op2.type.isReg -> OpEnc.MRV
		op1.type.isReg && op2.type.isMem -> OpEnc.RMV
		op1.type.isReg && op2.type.isReg && hasExt -> OpEnc.VMR
		else -> OpEnc.RVM
	}

	val isCompact get() = compactOps != CompactOps.NONE
	val opcode1 = opcode and 0xFF
	val opcode2 = opcode shr 8
	val hasExt get() = ext >= 0
}