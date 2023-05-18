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

	NONE(strings = arrayOf("void")),
	R_R(strings = arrayOf("reg8,reg8", "reg16,reg16", "reg32,reg32", "reg64,reg64")),
	RM_I(smStrings = arrayOf("rm8,imm", "rm16,imm", "rm32,imm", "rm64,imm")),
	A_I(smStrings = arrayOf("reg_al,imm", "reg_ax,imm", "reg_eax,imm", "reg_rax,imm")),
	R_M_I(smStrings = arrayOf(null, "reg16,mem,imm16", "reg32,mem,imm32", "reg64,mem,imm32")),
	M_R_CL(smStrings = arrayOf(null, "mem,reg16,reg_cl", "mem,reg32,reg_cl", "mem,reg64,reg_cl")),
	R_M_I8(smStrings = arrayOf(null, "reg16,mem,imm8", "reg32,mem,imm8", "reg64,mem,imm8")),
	M_R_I8(smStrings = arrayOf(null, "mem,reg16,imm", "mem,reg32,imm", "mem,reg64,imm")),

	MM_MM_I8(smStrings = arrayOf("mmxreg,mmxrm,imm")),
	X_M_I8(smStrings = arrayOf("xmmreg,mem,imm")),

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



enum class OperandType(val fixedWidth: Boolean = false) : OperandOrType {
	R,
	RM,
	M,
	MEM,
	I,
	I8(true),
	A,
	S,
	SM,
	REL,
	NONE;
}



enum class Operand(
	val type   : OperandType,
	val string : String? = null,
	var width  : Width? = null
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