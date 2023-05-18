package eyre.instructions

class NasmLine(
	val lineNumber : Int,
	val mnemonic   : String,
	val opString   : String,
	val string     : String,
) {

	var arch: Arch? = null
	var extension: Extension? = null
	var opSize = OpSize.NONE
	var sizeMatch = SizeMatch.NONE
	var argMatch = ArgMatch.NONE
	val sm get() = sizeMatch != SizeMatch.NONE
	var immWidth: ImmWidth = ImmWidth.NONE
	val opcodeParts = ArrayList<Int>()
	var plusC = false
	var plusR = false
	var evex: String? = null
	var vex: String? = null
	var ext = -1
	var hasModRM = false
	var has4 = false
	var encoding = ""
	var postModRM = -1
	var vsibPart: VsibPart? = null
	val opParts = ArrayList<OpPart>()
	val operands = ArrayList<Operand>()
	var mask = false
	var z = false
	var sae = false
	var er = false
	var b16 = false
	var b32 = false
	var b64 = false
	var rs2 = false
	var rs4 = false
	var star = false
	var width: Width? = null
	var ops = Operands.NONE
	fun set(ops: Operands, width: Width? = null) { this.ops = ops; this.width = width }
	override fun toString() = string

}