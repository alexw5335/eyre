package eyre.instructions

import eyre.encoding.Operand
import java.awt.image.ComponentSampleModel



class NasmGroup(val mnemonic: String) {
	val lines = ArrayList<NasmLine>()
}



val Char.isHex get() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private val set = HashSet<String>()

fun printUnique(string: String) {
	if(string in set) return
	set += string
	println(string)
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



enum class SizeMatch {
	NONE,
	SM,
	SM2;
}



enum class ArgMatch {
	NONE,
	AR0,
	AR1,
	AR2;
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
	F3I,
	WAIT;
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



enum class Arch {
	NONE,
	_8086,
	_186,
	_286,
	_386,
	_486,
	PENT,
	P6,
	KATMAI,
	WILLAMETTE,
	PRESCOTT,
	X86_64,
	NEHALEM,
	WESTMERE,
	SANDYBRIDGE,
	FUTURE,
	IA64;
}



enum class Extension {
	NONE,
	AES,
	AMXBF16,
	AMXINT8,
	AMXTILE,
	AVX,
	AVX2,
	AVX512,
	AVX5124FMAPS,
	AVX5124VNNIW,
	AVX512BF16,
	AVX512BITALG,
	AVX512BW,
	AVX512CD,
	AVX512DQ,
	AVX512ER,
	AVX512FC16,
	AVX512FP16,
	AVX512IFMA,
	AVX512PF,
	AVX512VBMI,
	AVX512VBMI2,
	AVX512VL,
	AVX512VNNI,
	AVX512VP2INTERSECT,
	AVX512VPOPCNTDQ,
	AVXIFMA,
	AVXNECONVERT,
	AVXVNNIINT8,
	BMI1,
	BMI2,
	CET,
	CMPCCXADD,
	ENQCMD,
	FMA,
	FPU,
	FRED,
	GFNI,
	HRESET,
	INVPCID,
	MMX,
	MPX,
	MSRLIST,
	PCONFIG,
	PREFETCHI,
	PREFETCHWT1,
	RAOINT,
	RTM,
	SERIALIZE,
	SGX,
	SHA,
	SSE,
	SSE2,
	SSE3,
	SSE41,
	SSE42,
	SSE4A,
	SSE5,
	SSSE3,
	TBM,
	TSXLDTRK,
	UINTR,
	VAES,
	VMX,
	VPCLMULQDQ,
	WBNOINVD,
	WRMSRNS,
	_3DNOW,
	NOT_GIVEN;

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



enum class Operands2(vararg val types: OperandOrType) {
	R_R(OperandType.R, OperandType.R),
	R_M(OperandType.R, OperandType.M),
	M_R(OperandType.M, OperandType.R),
	R_I(OperandType.R, OperandType.I),
	M(OperandType.M),
	MEM(OperandType.MEM),
	R(OperandType.R),
	RM(OperandType.RM),
}



sealed interface OperandOrType



enum class Operands(
	val strings: Array<String?>? = null,
	val smStrings: Array<String?>? = null,
	val sStrings: Array<String?>? = null,
) {

	M_R(
		strings = arrayOf("mem8,reg8", "mem16,reg16", "mem32,reg32", "mem64,reg64"),
		smStrings = arrayOf("mem,reg8", "mem,reg16", "mem,reg32", "mem,reg64")),

	R_M(
		strings = arrayOf("reg8,mem8", "reg16,mem16", "reg32,mem32", "reg64,mem64"),
		smStrings = arrayOf("reg8,mem", "reg16,mem", "reg32,mem", "reg64,mem")),

	R_R_I8(
		strings = arrayOf(null, "reg16,reg16,imm8", "reg32,reg32,imm8", "reg64,reg64,imm8"),
		smStrings = arrayOf(null, "reg16,reg16,imm", "reg32,reg32,imm", "reg64,reg64,imm")),

	RM_I(
		strings = arrayOf("rm8,imm8"),
		smStrings = arrayOf("rm8,imm", "rm16,imm", "rm32,imm", "rm64,imm")),

	NONE(strings = arrayOf("void")),
	R_R(strings = arrayOf("reg8,reg8", "reg16,reg16", "reg32,reg32", "reg64,reg64")),

	A_I(smStrings = arrayOf("reg_al,imm", "reg_ax,imm", "reg_eax,imm", "reg_rax,imm")),
	R_M_I(smStrings = arrayOf(null, "reg16,mem,imm16", "reg32,mem,imm32", "reg64,mem,imm32")),
	M_R_CL(smStrings = arrayOf(null, "mem,reg16,reg_cl", "mem,reg32,reg_cl", "mem,reg64,reg_cl")),
	R_M_I8(smStrings = arrayOf(null, "reg16,mem,imm8", "reg32,mem,imm8", "reg64,mem,imm8")),
	M_R_I8(smStrings = arrayOf(null, "mem,reg16,imm", "mem,reg32,imm", "mem,reg64,imm")),

	MM_MM_I8(smStrings = arrayOf("mmxreg,mmxrm,imm")),
	X_M_I8(smStrings = arrayOf("xmmreg,mem,imm")),

	R_RM(strings = arrayOf(null, "reg16,rm16", "reg32,rm32", "reg64,rm64")),
	R_I8(strings = arrayOf(null, "reg16,imm8", "reg32,imm8", "reg64,imm8")),
	RM_I8(strings = arrayOf(null, "rm16,imm8", "rm32,imm8", "rm64,imm8")),
	R(strings = arrayOf("reg8", "reg16", "reg32", "reg64")),
	RM(strings = arrayOf("rm8", "rm16", "rm32", "rm64")),
	M(strings = arrayOf("mem8", "mem16", "mem32", "mem64", "mem80", "mem128", "mem256", "mem512")),
	MEM(strings = arrayOf("mem")),
	ST(strings = arrayOf("fpureg")),
	ST_ST0(strings = arrayOf("fpureg,fpu0")),
	ST0_ST(strings = arrayOf("fpu0,fpureg")),
	A_I8,
	I8_A,
	R_R_I(strings = arrayOf(null, "reg16,reg16,imm16", "reg32,reg32,imm32", "reg64,reg64,imm32")),
	A(strings = arrayOf(null, "reg_ax")),
	I(strings = arrayOf(null, "imm16", "imm32")),
	R_I(strings = arrayOf(null, "reg16,imm16", "reg32,imm32", "reg64,imm32")),
	RM_1(strings = arrayOf("rm8,unity", "rm16,unity", "rm32,unity", "rm64,unity")),
	RM_CL(strings = arrayOf("rm8,reg_cl","rm16,reg_cl","rm32,reg_cl","rm64,reg_cl")),
	S_R8(sStrings = arrayOf("xmmreg,reg8", "ymmreg,reg8", "zmmreg,reg8")),
	S_R16(sStrings = arrayOf("xmmreg,reg16", "ymmreg,reg16", "zmmreg,reg16")),
	S_R32(sStrings = arrayOf("xmmreg,reg32", "ymmreg,reg32", "zmmreg,reg32")),
	S_R64(sStrings = arrayOf("xmmreg,reg64", "ymmreg,reg64", "zmmreg,reg64"));
}



enum class OperandType : OperandOrType {
	R,
	RM,
	M,
	MEM,
	I,
	I8,
	A,
	S,
	SM,
	REL,
	NONE;
}



enum class Operand2 {
	R8,
	R16,
	R32,
	R64,
	M,
	M8,
	M16,
	M32,
	M64,
	M80,
	M128,
	M256,
	M512,
	I8,
	I16,
	I32,
	I64,
	AL,
	AX,
	EAX,
	RAX,
	DX,
	CL,
	ONE,
	REL8,
	REL16,
	REL32,
	ST,
	ST0,
	MM,
	X,
	X0,
	Y,
	Z,
	VM32X,
	VM64X,
	VM32Y,
	VM64Y,
	VM32Z,
	VM64Z,
	K,
	BND,
	T;
}



enum class RawCompoundOperand(val string: String){
	RM8("rm8"),
	RM16("rm16"),
	RM32("rm32"),
	RM64("rm64"),
	MMM64("mmxrm64"),
	XM8("xmmrm8"),
	XM16("xmmrm16"),
	XM32("xmmrm32"),
	XM64("xmmrm64"),
	XM128("xmmrm128"),
	XM256("xmmrm256"),
	YM16("ymmrm16"),
	YM128("ymmrm128"),
	YM256("ymmrm256"),
	ZM16("zmmrm16"),
	ZM128("zmmrm128"),
	ZM512("zmmrm512"),
	KM8("krm8"),
	KM16("krm16"),
	KM32("krm32"),
	KM64("krm64"),
}



enum class RawOperand(
	val type   : OperandType,
	val string : String? = null,
	var width  : Width? = null,
) : OperandOrType {

	NONE(OperandType.NONE, "void", null),

	R8(OperandType.R, "reg8", Width.BYTE),
	R16(OperandType.R, "reg16", Width.WORD),
	R32(OperandType.R, "reg32", Width.DWORD),
	R64(OperandType.R, "reg64", Width.QWORD),

	RM8(OperandType.RM, "rm8", Width.BYTE),
	RM16(OperandType.RM, "rm16", Width.WORD),
	RM32(OperandType.RM, "rm32", Width.DWORD),
	RM64(OperandType.RM, "rm64", Width.QWORD),

	MEM(OperandType.MEM),
	M8(OperandType.M, "mem8", Width.BYTE),
	M16(OperandType.M, "mem16", Width.WORD),
	M32(OperandType.M, "mem32", Width.DWORD),
	M64(OperandType.M, "mem64", Width.QWORD),
	M80(OperandType.M, "mem80", Width.TWORD),
	M128(OperandType.M, "mem128", Width.XWORD),
	M256(OperandType.M, "mem256", Width.YWORD),
	M512(OperandType.M, "mem512", Width.ZWORD),

	I8(OperandType.I8, "imm8",  Width.BYTE),
	I16(OperandType.I, "imm16", Width.WORD),
	I32(OperandType.I, "imm32", Width.DWORD),
	I64(OperandType.I, "imm64", Width.QWORD),

	AL(OperandType.A, "reg_al", Width.BYTE),
	AX(OperandType.A, "reg_ax", Width.WORD),
	EAX(OperandType.A, "reg_eax", Width.DWORD),
	RAX(OperandType.A, "reg_rax", Width.QWORD),
	DX(OperandType.NONE, "reg_dx", Width.WORD),
	CL(OperandType.NONE, "reg_cl", Width.BYTE),
	ONE(OperandType.NONE, "unity"),

	REL8(OperandType.REL, null, Width.BYTE),
	REL16(OperandType.REL, null, Width.WORD),
	REL32(OperandType.REL, null, Width.DWORD),

	ST(OperandType.NONE, "fpureg", Width.TWORD),
	ST0(OperandType.NONE, "fpu0", Width.TWORD),

	MM(OperandType.NONE, "mmxreg", Width.QWORD),
	MMM64(OperandType.NONE, "mmxrm64"),

	X(OperandType.S, "xmmreg", Width.XWORD),
	X0(OperandType.NONE, "xmm0", Width.XWORD),
	XM8(OperandType.NONE, "xmmrm8"),
	XM16(OperandType.NONE, "xmmrm16"),
	XM32(OperandType.NONE, "xmmrm32"),
	XM64(OperandType.NONE, "xmmrm64"),
	XM128(OperandType.SM, "xmmrm128"),
	XM256(OperandType.NONE, "xmmrm256"),

	Y(OperandType.S, "ymmreg", Width.YWORD),
	YM16(OperandType.NONE, "ymmrm16"),
	YM128(OperandType.NONE, "ymmrm128"),
	YM256(OperandType.SM, "ymmrm256"),

	Z(OperandType.S, "zmmreg", Width.ZWORD),
	ZM16(OperandType.NONE, "zmmrm16"),
	ZM128(OperandType.NONE, "zmmrm128"),
	ZM512(OperandType.SM, "zmmrm512"),

	VM32X(OperandType.NONE, "xmem32"),
	VM64X(OperandType.NONE, "xmem64"),
	VM32Y(OperandType.NONE, "ymem32"),
	VM64Y(OperandType.NONE, "ymem64"),
	VM32Z(OperandType.NONE, "zmem32"),
	VM64Z(OperandType.NONE, "zmem64"),

	K(OperandType.NONE, "kreg"),
	KM8(OperandType.NONE, "krm8"),
	KM16(OperandType.NONE, "krm16"),
	KM32(OperandType.NONE, "krm32"),
	KM64(OperandType.NONE, "krm64"),

	BND(OperandType.NONE, "bndreg"),

	T(OperandType.NONE, "tmmreg");

}



enum class VexM {
	NONE,
	M0F,
	M38,
	M3A;
}


enum class VexP {
	NONE,
	M66,
	MF2,
	MF3;
}

enum class VexW {
	W0,
	W1,
	WI;
}

enum class VexL {
	L0,
	L1,
	LI
}