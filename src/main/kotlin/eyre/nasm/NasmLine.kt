package eyre.nasm

import eyre.Width

class NasmLine(val raw: RawNasmLine) {

	val mnemonic = raw.mnemonic
	var arch = NasmArch.NONE
	val extensions = ArrayList<NasmExt>()
	var opSize: Width? = null
	var sm = false
	var ar = -1
	var immType = ImmType.NONE
	var opcode = 0
	var oplen = 0
	var cc = false
	var opreg = false
	var evex: String? = null
	var vex: String? = null
	var opcodeExt = -1
	var modrm = false
	var is4 = false
	var opEnc = OpEnc.NONE
	var tupleType: TupleType? = null
	var postModRM = -1
	var vsib = VSib.NONE
	val opParts = ArrayList<OpPart>()
	var prefix = 0
	var rexw = false
	var k = false
	var z = false
	var sae = false
	var er = false
	var b16 = false
	var b32 = false
	var b64 = false
	var rs2 = false
	var rs4 = false
	var star = false
	var a32 = false
	var o16 = false

	var vexl = VexL.NONE
	var vexw = VexW.NONE
	var vexPrefix = VexPrefix.NP
	var vexExt = VexExt.E0F
	var map5 = false
	var map6 = false

	var compoundIndex = -1
	var compound: NasmOp? = null

	var escape = 0

	val operands = ArrayList<NasmOp>()

	fun addedOpcode(value: Int) = opcode + (value shl ((oplen - 1) shl 3))

	fun addOperand(operand: NasmOp) {
		if(operand.parts.isNotEmpty()) {
			compound = operand
			compoundIndex = operands.size
		}
		operands += operand
	}

	fun addOpcode(value: Int) {
		if(oplen == 0) when(value) {
			0x0F -> if(escape == 0) { escape = 1; return }
			0x38 -> if(escape == 1) { escape = 2; return }
			0x3A -> if(escape == 1) { escape = 3; return }
		}
		opcode = opcode or (value shl (oplen shl 3))
		oplen++
	}

	override fun toString() = raw.toString()

}
