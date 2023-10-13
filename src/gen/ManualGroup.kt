package eyre.gen

/**
 * Needs to fit into 32 bits
 * - Opcode: 8
 * - Operands: 8
 * - Mask: 4
 * - Prefix: 2
 * - Escape: 2
 * - Extension: 4
 */
class Enc(
	val opcode: Int,
)

class ManualGroup