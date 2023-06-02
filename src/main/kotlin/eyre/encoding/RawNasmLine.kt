package eyre.encoding

class RawNasmLine(
	val lineNumber : Int,
	val mnemonic   : String,
	val operands   : String,
	val parts      : List<String>,
	val extras     : List<String>
) {
	override fun toString() = "$lineNumber: $mnemonic $operands $parts $extras"
}