package eyre.instructions



class RawNasmLine(
	val lineNumber     : Int,
	val mnemonic       : String,
	val operandsString : String,
	val operands       : List<String>,
	val opcodeParts    : List<String>,
	val extras         : List<String>
) {
	override fun toString() = "$mnemonic $operandsString $opcodeParts $extras"
}



data class ProcessedLine(
	val raw       : NasmLine,
	val mnemonic  : String,
	val arch      : Arch,
	val extension : Extension,
	val opcodeExt : Int,
	val opcode    : List<Int>,
	val operands  : List<RawOperand>,
	val k         : Boolean,
	val z         : Boolean,
	val sae       : Boolean,
	val er        : Boolean,
	val b16       : Boolean,
	val b32       : Boolean,
	val b64       : Boolean,
)



fun NasmLine.toProcessedLine() = ProcessedLine(
	this, mnemonic, arch, extension, ext, opcodeParts, operands, k, k, sae, er, b16, b32, b64
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
	var sizeMatch   = SizeMatch.NONE
	var argMatch    = ArgMatch.NONE
	var immWidth    = ImmWidth.NONE
	val opcodeParts = ArrayList<Int>()
	var plusC       = false
	var plusR       = false

	var evex: String? = null
	var vex: String? = null

	var ext       = -1
	var hasModRM  = false
	var has4      = false
	var encoding  = ""
	var postModRM = -1
	var vsibPart  = VsibPart.NONE
	val opParts   = ArrayList<OpPart>()
	val operands  = ArrayList<RawOperand>()

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


	var width: Width? = null
	var ops = Operands.NONE
	fun set(ops: Operands, width: Width) { this.ops = ops; this.width = width }
	fun set(ops: Operands) { this.ops = ops; this.width = null }
	override fun toString() = string

}