package eyre.encoding

import eyre.Encoding
import eyre.Mnemonic
import eyre.OpMask

data class ParsedEncoding(
    val mnemonic  : Mnemonic,
    val prefix    : Int,
    val escape    : Int,
    val opcode    : Int,
    val oplen     : Int,
    val extension : Int,
    val operands  : Ops,
    val opMask    : OpMask,
    val opMask2   : OpMask,
    val rexw      : Boolean,
    val o16       : Boolean,
) {

	val encoding = Encoding(
        opcode,
        prefix,
        escape,
        extension,
        opMask,
        opMask2,
        oplen,
        if (rexw) 1 else 0,
        if (o16) 1 else 0
    )

}