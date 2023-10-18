package eyre.gen

import eyre.Escape
import eyre.Mnemonic
import eyre.Prefix

data class ManualLine(
    val lineNum  : Int,
    val mnemonic : String,
    val prefix   : Prefix,
    val escape   : Escape,
    val opcode   : Int,
    val ext      : Int,
    val ops      : String,
    val mask     : Int,
    val rw       : Int,
    val o16      : Int,
	val a32      : Int,
    val noRw     : Int,
    val pseudo   : Int,
)