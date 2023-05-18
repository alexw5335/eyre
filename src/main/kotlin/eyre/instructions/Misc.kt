package eyre.instructions



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
	REL8,
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
	FPU,
	MMX,
	_3DNOW,
	SSE,
	SSE2,
	SSE3,
	VMX,
	SSSE3,
	SSE4A,
	SSE41,
	SSE42,
	SSE5,
	AVX,
	AVX2,
	FMA,
	BMI1,
	BMI2,
	TBM,
	RTM,
	INVPCID,
	AVX512,
	AVX512CD,
	AVX512ER,
	AVX512PF,
	MPX,
	SHA,
	PREFETCHWT1,
	AVX512VL,
	AVX512DQ,
	AVX512BW,
	AVX512IFMA,
	AVX512VBMI,
	AES,
	VAES,
	VPCLMULQDQ,
	GFNI,
	AVX512VBMI2,
	AVX512VNNI,
	AVX512BITALG,
	AVX512VPOPCNTDQ,
	AVX5124FMAPS,
	AVX5124VNNIW,
	AVX512FP16,
	AVX512FC16,
	SGX,
	CET,
	ENQCMD,
	PCONFIG,
	WBNOINVD,
	TSXLDTRK,
	SERIALIZE,
	AVX512BF16,
	AVX512VP2INTERSECT,
	AMXTILE,
	AMXBF16,
	AMXINT8,
	FRED,
	RAOINT,
	UINTR,
	CMPCCXADD,
	PREFETCHI,
	WRMSRNS,
	MSRLIST,
	AVXNECONVERT,
	AVXVNNIINT8,
	AVXIFMA,
	HRESET;
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



enum class Operands(val widthIndex: Int = 0, vararg val types: OperandType) {
	NONE,
	R_R(0, OperandType.R, OperandType.R),
	M_R(1, OperandType.M, OperandType.R),
	R_M(0, OperandType.R, OperandType.M),
	RM_I8(0, OperandType.RM, OperandType.I8),
	RM_I(0, OperandType.RM, OperandType.I),
	A_I(0, OperandType.A, OperandType.I)
}



enum class OperandType {
	R,
	RM,
	M,
	I,
	A,
	S,
	SM,
	REL,
	NONE
}



enum class Operand(
	val type   : OperandType,
	val string : String? = null,
	var width  : Width? = null
) {

	NONE(OperandType.NONE, "void", null),

	R8 (OperandType.R, "reg8",  Width.BYTE),
	R16(OperandType.R, "reg16", Width.WORD),
	R32(OperandType.R, "reg32", Width.DWORD),
	R64(OperandType.R, "reg64", Width.QWORD),

	RM8 (OperandType.RM, "rm8",  Width.BYTE),
	RM16(OperandType.RM, "rm16", Width.WORD),
	RM32(OperandType.RM, "rm32", Width.DWORD),
	RM64(OperandType.RM, "rm64", Width.QWORD),

	M   (OperandType.M),
	M8  (OperandType.M, "mem8",   Width.BYTE),
	M16 (OperandType.M, "mem16",  Width.WORD),
	M32 (OperandType.M, "mem32",  Width.DWORD),
	M64 (OperandType.M, "mem64",  Width.QWORD),
	M80 (OperandType.M, "mem80",  Width.TWORD),
	M128(OperandType.M, "mem128", Width.XWORD),
	M256(OperandType.M, "mem256", Width.YWORD),
	M512(OperandType.M, "mem512", Width.ZWORD),

	I8 (OperandType.I, "imm8",  Width.BYTE),
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