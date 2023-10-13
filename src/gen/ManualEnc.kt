package eyre.gen

import eyre.Escape
import eyre.Ops
import eyre.Prefix

/**
 * - Single-byte opcode
 * - Operands of 8, 16, 32, or 64-bit width
 * - Any GP operands that don't meet these conditions must be handled separately.
 */
data class ManualEnc(
    val mnemonic: String,
    val prefix: Prefix,
    val escape: Escape,
    val opcode: Int,
    val ext: Int,
    val mask: Int,
    val ops: Ops,
    val rw: Int,
    val o16: Int
)