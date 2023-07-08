package eyre.gen

import eyre.Escape
import eyre.Prefix
import eyre.util.hexc8

class NasmEnc(
	val mnemonic : String,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val ext      : Int,
	val ops      : List<Op>,
	val rexw     : Int,
	val o16      : Int,
	val pseudo   : Int,
	val mr       : Boolean,
	val isAvx    : Boolean,
	val exts     : List<NasmExt>,
	val opEnc    : OpEnc,
) {

	fun equals(other: NasmEnc, ignoreOps: Boolean) =
		mnemonic == other.mnemonic &&
		prefix == other.prefix &&
		escape == other.escape &&
		opcode == other.opcode &&
		ext == other.ext &&
		rexw == other.rexw &&
		o16 == other.o16 &&
		pseudo == other.pseudo &&
		isAvx == other.isAvx &&
		(ignoreOps || ops == other.ops)

	override fun toString() = buildString {
		when(prefix) {
			Prefix.NONE -> { }
			Prefix.P66 -> append("66 ")
			Prefix.PF2 -> append("F2 ")
			Prefix.PF3 -> append("F3 ")
			Prefix.P9B -> append("9B ")
			Prefix.P67 -> append("67 ")
		}

		when(escape) {
			Escape.NONE -> { }
			Escape.E0F -> append("0F ")
			Escape.E38 -> append("0F 38 ")
			Escape.E3A -> append("0F 3A ")
		}

		if(opcode and 0xFF00 != 0) {
			append((opcode shr 8).hexc8)
			append(' ')
		}

		append((opcode and 0xFF).hexc8)
		if(ext >= 0) append("/$ext")
		append("  ")
		append(mnemonic)
		append("  ")
		if(ops.isNotEmpty())
			append(ops.joinToString("_"))
		else
			append("NONE")
		append("  ")
		if(rexw == 1) append("RW ")
		if(o16 == 1) append("O16 ")
		if(pseudo >= 0) append(":$pseudo ")
		if(mr) append("MR ")
	}

}