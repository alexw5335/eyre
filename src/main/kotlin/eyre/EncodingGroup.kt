package eyre

import eyre.util.hex

class EncodingGroup(val mnemonic: Mnemonic) {

	var ops = 0L
	val encodings = ArrayList<Encoding>()


	fun add(encoding: Encoding) {
		if(encoding.operands in this)
			return
		if(encoding.operands.ordinal < 64)
			ops = ops or (1L shl encoding.operands.ordinal)
		encodings += encoding
	}

	operator fun get(ops: Ops) = encodings[(this.ops and ((1L shl ops.ordinal) - 1)).countOneBits()]
	operator fun contains(operands: Ops) = this.ops and (1L shl operands.ordinal) != 0L

	override fun toString() = "Group($mnemonic, ${ops.hex}, ${encodings.joinToString()})"
	
}