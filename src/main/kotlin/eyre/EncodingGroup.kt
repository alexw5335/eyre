package eyre

import eyre.util.bin8888

class EncodingGroup(val opCount: Int, val mnemonic: Mnemonic) {
	var ops = 0
	var specs = 0
	val encodings = ArrayList<Encoding>()

	fun add(encoding: Encoding) {
		ops = ops or (1 shl encoding.operands.ordinal)
		specs = specs or (1 shl encoding.operands.spec.ordinal)
		encodings += encoding
	}

	operator fun get(ops: Ops) = encodings[(this.ops and ((1 shl ops.ordinal) - 1)).countOneBits()]
	operator fun contains(operands: Ops) = this.ops and (1 shl operands.ordinal) != 0
	operator fun contains(spec: Spec) = this.specs and (1 shl spec.ordinal) != 0

	override fun toString() = "Group$opCount($mnemonic, ${ops.bin8888}, ${specs.bin8888}, ${encodings.joinToString()})"
	
}