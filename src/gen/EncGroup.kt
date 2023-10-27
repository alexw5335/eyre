package eyre.gen

import eyre.Mnemonic
import eyre.Ops

class EncGroup(val mnemonic: Mnemonic) {
	var ops = 0
	var isCompact = false
	val encs = ArrayList<ParsedEnc>()
	val allEncs = ArrayList<ParsedEnc>()
	private val Ops.index get() = (ops and ((1 shl ordinal) - 1)).countOneBits()
	operator fun get(ops: Ops) = encs[ops.index]
	operator fun contains(operands: Ops) = this.ops and (1 shl operands.ordinal) != 0
}