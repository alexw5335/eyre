package eyre

import eyre.util.bin8888

class EncodingGroup(val mnemonic: Mnemonic) {

	var ops = 0
	val encodings = ArrayList<Encoding>()


	fun add(encoding: Encoding) {
		if(encoding.operands in this)
			return
		if(encoding.operands.ordinal < 32)
			ops = ops or (1 shl encoding.operands.ordinal)
		encodings += encoding
	}


	fun getCustom(ops: Ops) = encodings.first { it.operands == ops }

	operator fun get(ops: Ops) = encodings[(this.ops and ((1 shl ops.ordinal) - 1)).countOneBits()]
	operator fun contains(operands: Ops) = this.ops and (1 shl operands.ordinal) != 0

	override fun toString() = "Group($mnemonic, ${ops.bin8888}, ${encodings.joinToString()})"
	
}