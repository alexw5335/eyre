package eyre.nasm

class RawNasmLine(
	val lineNumber: Int,
	val mnemonic: String,
	val operands: List<String>,
	val parts: List<String>,
	val extras: List<String>
) {
	override fun toString() = "$lineNumber: $mnemonic $operands $parts $extras"
}