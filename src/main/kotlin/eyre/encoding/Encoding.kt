package eyre.encoding

import eyre.RegMask



enum class SimdPrefix(val value: Int) {
	NONE(0),
	P66(1),
	PF3(2),
	PF2(3);
}



enum class OpcodePrefix(val value: Int) {
	NONE(0),
	P0F(1),
	P38(2),
	P3A(3);
}



/**
 * Only for encodings with unambiguous operands.
 */
enum class Operands {
	R,
	M,
	I,
	R_R,
	R_M,
	M_R,
	R_I,
	M_I,

	// Custom
	A_I8,
	I8_A,
	A_DX,
	DX_A,
	R_CL,
	M_CL,
	I16_I8;
}



/**
 * - Bits 0-7: opcode
 * - Bits 8-8: VEX.W
 * - Bits 9-9: VEX.L
 * - Bits 9-10: prefix
 * - Bits 11-12: extension
 */
@JvmInline
value class VexInfo(val value: Int) {
	val opcode       get() = value and 0xFF
	val vexW         get() = (value shr 8) and 0b1
	val prefix       get() = (value shr 9) and 0b11
	val extension    get() = value shr 11
	val requiresVex3 get() = prefix > 1
	val incremented  get() = VexInfo(value + 1)

	companion object {
		const val E00 = 0 shl 11
		const val E66 = 1 shl 11
		const val EF3 = 2 shl 11
		const val EF2 = 3 shl 11
		const val P0F = 1 shl 9
		const val P38 = 2 shl 9
		const val P3A = 3 shl 9
		const val W0  = 0 shl 8
		const val W1  = 1 shl 8
		const val WIG = 1 shl 8
	}
}



enum class Operand {
	R8,
	R16,
	R32,
	R64,
	M,
	M8,
	M16,
	M32,
	M64,
	M128,
	M256,
	M512,
	AL,
	AX,
	EAX,
	RAX,
	CL,
	DX,
	I8,
	I16,
	I32,
	I64,
	X,
	Y,
	Z,
	ST,
	ST0,
}



class Encoding(
	val mnemonic     : String,
	val opcode       : Int,
	val opcodeLength : Int,
	val prefix       : Int,
	val extension    : Int,
	val operands     : Operands,
	val regMask      : RegMask
)