package eyre.instructions



data class NasmEncoding(
	val raw       : NasmLine,
	val mnemonic  : String,
	val arch      : Arch,
	val extension : Extension,
	val opcodeExt : Int,
	val opcode    : Int,
	val oplen     : Int,
	val prefix    : Int,
	val rexw      : Boolean,
	val op1       : NasmOperand?,
	val op2       : NasmOperand?,
	val op3       : NasmOperand?,
	val op4       : NasmOperand?,
) {

	val operands: List<NasmOperand> = ArrayList<NasmOperand>().apply {
		op1?.let(::add)
		op2?.let(::add)
		op3?.let(::add)
		op4?.let(::add)
	}

	override fun toString() = buildString {
		if(prefix != 0) append("${prefix.hex8} ")
		for(i in 0 until oplen) {
			val value = (opcode shr (i shl 3)) and 0xFF
			append(value.hex8)
			append(' ')
		}
		deleteAt(length - 1)
		if(opcodeExt >= 0) {
			append('/')
			append(opcodeExt)
		}
		append("  ")
		append(mnemonic)
		append("  ")
		for((i, operand) in operands.withIndex()) {
			append(operand)
			if(i != operands.lastIndex) append('_')
		}
	}

}



fun NasmLine.toProcessedLine(
	mnemonic: String,
	op1: NasmOperand?,
	op2: NasmOperand?,
	op3: NasmOperand?,
	op4: NasmOperand?
) = NasmEncoding(
	this, mnemonic, arch, extension, opcodeExt, opcode, oplen, prefix, rexw, op1, op2, op3, op4
)



class NasmLine(
	val lineNumber : Int,
	val mnemonic   : String,
	val opString   : String,
	val string     : String,
) {

	var arch        = Arch.NONE
	var extension   = Extension.NONE
	var opSize      = OpSize.NONE
	var sm  = false
	var ar0 = false
	var ar1 = false
	var ar2 = false
	val ar get() = ar0 || ar1 || ar2
	var immWidth = ImmWidth.NONE
	var opcode = 0
	var oplen  = 0
	var cc     = false
	var opreg  = false

	fun addOpcode(value: Int) {
		opcode = opcode or (value shl (oplen shl 3))
		oplen++
	}

	var evex: String? = null
	var vex: String? = null

	var opcodeExt = -1
	var hasModRM  = false
	var has4      = false
	var encoding  = ""
	var postModRM = -1
	var vsibPart  = VsibPart.NONE
	val opParts   = ArrayList<OpPart>()
	val operands  = ArrayList<NasmOperand>()
	var prefix    = 0
	var rexw      = false
	var k    = false
	var z    = false
	var sae  = false
	var er   = false
	var b16  = false
	var b32  = false
	var b64  = false
	var rs2  = false
	var rs4  = false
	var star = false

	override fun toString() = string

}