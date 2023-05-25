package eyre.instructions

import eyre.Reg
import eyre.RegType



class NasmGroup(val mnemonic: String) {
	val lines = ArrayList<NasmLine>()
}



val Int.hex8 get() = Integer.toHexString(this).uppercase().let { if(it.length == 1) "0$it" else it }

val Char.isHex get() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private val set = HashSet<String>()

fun printUnique(string: String) {
	if(string in set) return
	set += string
	println(string)
}



val ccList = arrayOf(
	arrayOf("O"),
	arrayOf("NO"),
	arrayOf("B", "NAE", "C"),
	arrayOf("NB", "AE", "NC"),
	arrayOf("Z", "E"),
	arrayOf("NZ", "NE"),
	arrayOf("BE", "NA"),
	arrayOf("NBE", "A"),
	arrayOf("S"),
	arrayOf("NS"),
	arrayOf("P", "PE"),
	arrayOf("NP", "PO"),
	arrayOf("L", "NGE"),
	arrayOf("NL", "GE"),
	arrayOf("LE", "NG"),
	arrayOf("NLE", "JG")
)



@JvmInline
value class Opcode(val backing: Int) {
	val value get() = backing and 0xFFFFFF
	val length get() = backing shr 24
}



enum class VsibPart {
	NONE,
	VM32X,
	VM64X,
	VM64Y,
	VM32Y,
	VSIBX,
	VSIBY,
	VSIBZ;
}



enum class OpPart {
	A32,
	A64,
	O16,
	O32,
	O64NW,
	O64,
	ODF,
	F2I,
	F3I;
}



enum class ImmWidth {
	NONE,
	IB,
	IW,
	ID,
	IQ,
	IB_S,
	IB_U,
	ID_S,
	REL,
	REL8;

	val is8  get() = this == IB || this == IB_S || this == IB_U
	val is16 get() = this == IW
	val is32 get() = this == ID || this == ID_S
	val is64 get() = this == IQ
}



enum class OpSize {
	NONE,
	SB,
	SW,
	SD,
	SQ,
	SO,
	SY,
	SZ,
	SX;
}



enum class Width {
	BYTE,
	WORD,
	DWORD,
	QWORD,
	TWORD,
	XWORD,
	YWORD,
	ZWORD;
}



enum class OperandType {
	R,
	M,
	I,
	A,
	C,
	D,
	S,
	ST,
	K,
	MM,
	REL,
	COMPOUND,
	ONE,
	MISC;
}



enum class NasmOperands {
	R,
	M,
	I,
	R_R,
	R_M,
	M_R,
	R_I,
	M_I,
	A_I,
	R_I8,
	M_I8,
	R_1,
	M_1,
	R_CL,
	M_CL,
	ST_ST0,
	ST0_ST,
	X_X,
	X_M,

	REL8_ECX,
	REL8_RCX;
}



enum class NasmOperand(
	val type: OperandType,
	val string: String?,
	val width: Width? = null,
	vararg val parts: NasmOperand
) {

	R8(OperandType.R, "reg8", Width.BYTE),
	R16(OperandType.R, "reg16", Width.WORD),
	R32(OperandType.R, "reg32", Width.DWORD),
	R64(OperandType.R, "reg64", Width.QWORD),
	MEM(OperandType.M, null, null),
	M8(OperandType.M, "mem8", Width.BYTE),
	M16(OperandType.M, "mem16", Width.WORD),
	M32(OperandType.M, "mem32", Width.DWORD),
	M64(OperandType.M, "mem64", Width.QWORD),
	M80(OperandType.M, "mem80", Width.TWORD),
	M128(OperandType.M, "mem128", Width.XWORD),
	M256(OperandType.M, "mem256", Width.YWORD),
	M512(OperandType.M, "mem512", Width.ZWORD),
	I8(OperandType.I, "imm8", Width.BYTE),
	I16(OperandType.I, "imm16", Width.WORD),
	I32(OperandType.I, "imm32", Width.DWORD),
	I64(OperandType.I, "imm64", Width.QWORD),
	AL(OperandType.A, "reg_al", Width.BYTE),
	AX(OperandType.A, "reg_ax", Width.WORD),
	EAX(OperandType.A, "reg_eax", Width.DWORD),
	RAX(OperandType.A, "reg_rax", Width.QWORD),
	DX(OperandType.D, "reg_dx", Width.WORD),
	CL(OperandType.C, "reg_cl", Width.BYTE),
	ECX(OperandType.C, "reg_ecx", Width.DWORD),
	RCX(OperandType.C, "reg_rcx", Width.QWORD),
	ONE(OperandType.ONE, "unity"),
	REL8(OperandType.REL, null, Width.BYTE),
	REL16(OperandType.REL, null, Width.WORD),
	REL32(OperandType.REL, null, Width.DWORD),
	ST(OperandType.ST, "fpureg", Width.TWORD),
	ST0(OperandType.ST, "fpu0", Width.TWORD),
	MM(OperandType.MM, "mmxreg", Width.QWORD),
	X(OperandType.S, "xmmreg", Width.XWORD),
	X0(OperandType.S, "xmm0", Width.XWORD),
	Y(OperandType.S, "ymmreg", Width.YWORD),
	Z(OperandType.S, "zmmreg", Width.ZWORD),
	VM32X(OperandType.MISC, "xmem32"),
	VM64X(OperandType.MISC, "xmem64"),
	VM32Y(OperandType.MISC, "ymem32"),
	VM64Y(OperandType.MISC, "ymem64"),
	VM32Z(OperandType.MISC, "zmem32"),
	VM64Z(OperandType.MISC, "zmem64"),
	K(OperandType.K, "kreg"),
	BND(OperandType.MISC, "bndreg"),
	T(OperandType.MISC, "tmmreg"),

	RM8(OperandType.COMPOUND, "rm8", Width.BYTE, R8, M8),
	RM16(OperandType.COMPOUND, "rm16", Width.WORD, R16, M16),
	RM32(OperandType.COMPOUND, "rm32", Width.DWORD, R32, M32),
	RM64(OperandType.COMPOUND, "rm64", Width.QWORD, R64, M64),
	MMM64(OperandType.COMPOUND, "mmxrm64", Width.QWORD, MM, M64),
	XM8(OperandType.COMPOUND, "xmmrm8", null, X, M8),
	XM16(OperandType.COMPOUND, "xmmrm16", null, X, M16),
	XM32(OperandType.COMPOUND, "xmmrm32", null, X, M32),
	XM64(OperandType.COMPOUND, "xmmrm64", null, X, M64),
	XM128(OperandType.COMPOUND, "xmmrm128", null, X, M128),
	XM256(OperandType.COMPOUND, "xmmrm256", null, X, M256),
	YM16(OperandType.COMPOUND, "ymmrm16", null, Y, M16),
	YM128(OperandType.COMPOUND, "ymmrm128", null, Y, M128),
	YM256(OperandType.COMPOUND, "ymmrm256", null, Y, M256),
	ZM16(OperandType.COMPOUND, "zmmrm16", null, Z, M16),
	ZM128(OperandType.COMPOUND, "zmmrm128", null, Z, M128),
	ZM512(OperandType.COMPOUND, "zmmrm512", null, Z, M512),
	KM8(OperandType.COMPOUND, "krm8", null, K, M8),
	KM16(OperandType.COMPOUND, "krm16", null, K, M16),
	KM32(OperandType.COMPOUND, "krm32", null, K, M32),
	KM64(OperandType.COMPOUND, "krm64", null, K, M64);

}