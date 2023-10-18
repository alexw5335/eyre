package eyre.gen

import eyre.*

data class ManualEnc(
	val mnemonic: String,
	val prefix: Prefix,
	val escape: Escape,
	val opcode: Int,
	val ext: Int,
	val mask: Int,
	val ops: CompactOps,
	val rw: Int,
	val o16: Int,
	val a32: Int,
	val manualOps: List<ManualOp>,
	val vex: Boolean,
	val vexw: VexW,
	val vexl: VexL
) {
	val opcode1 = opcode and 0xFF
	val opcode2 = opcode shr 8
	val hasExt get() = ext >= 0
}