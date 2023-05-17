package eyre.instructions



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



enum class NasmOperands {
	M8_R8,
	M16_R16,
	M32_R32,
	M64_R64,
	R8_M8,
	R16_M16,
	R32_M32,
	R64_M64,
	R8_R8,
	R16_R16,
	R32_R32,
	R64_R64,
	AL_I8,
	AX_I16,
	EAX_I32,
	RAX_I32,
	RM16_I8,
	RM32_I8,
	RM64_I8,
	RM8_I8,
	RM16_I16,
	RM32_I32,
	RM64_I32,
	R32,
	R64,
	M8_I8,
	M16_I16,
	M32_I32,
	M64_I32,
	AX_I8,
	EAX_I8,
	I8_AL,
	I8_AX,
	I8_EAX,
	AL_DX,
	AX_DX,
	EAX_DX,
	DX_AL,
	DX_AX,
	DX_EAX,
	R16_M16_I8,
	R16_M16_I16,
	R16_R16_I16,
	R32_M32_I8,
	R32_M32_I32,
	R32_R32_I32,
	R64_M64_I8,
	R64_M64_I32,
	R64_R64_I32,
	R8_I8,
	R16_I16,
	R32_I32,
	R64_I32,
	AL_MOFFS,
	AX_MOFFS,
	EAX_MOFFS,
	RAX_MOFFS,
	MOFFS_AL,
	MOFFS_AX,
	MOFFS_EAX,
	MOFFS_RAX,
	M16_R16_I16,
	M32_R32_I32,
	M64_R64_I32,
	M16_R16_CL,
	M32_R32_CL,
	M64_R64_CL,

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



enum class Operands {
	RM_I8,
	M_R,
	R_M,
	RM_I,
	R,
	M_I,
	R_M_I,
	DX_A,
	A_DX,
	I8_A,
	A_I8,
	M_R_CL,
	M_R_I,
	R_I,
	MOFFS_A,
	A_MOFFS,
	R_R_I,
}



enum class Operand(val string: String? = null, var width: Width? = null) {
	R8("reg8", Width.BYTE),
	R16("reg16", Width.WORD),
	R32("reg32", Width.DWORD),
	R64("reg64", Width.QWORD),
	RM8("rm8", Width.BYTE),
	RM16("rm16", Width.WORD),
	RM32("rm32", Width.DWORD),
	RM64("rm64", Width.QWORD),
	M8("mem8", Width.BYTE),
	M16("mem16", Width.WORD),
	M32("mem32", Width.DWORD),
	M64("mem64", Width.QWORD),
	M80("mem80", Width.TWORD),
	M128("mem128", Width.XWORD),
	M256("mem256", Width.YWORD),
	M512("mem512", Width.ZWORD),
	M,
	I8("imm8", Width.BYTE),
	I16("imm16", Width.WORD),
	I32("imm32", Width.DWORD),
	I64("imm64", Width.QWORD),
	AL("reg_al", Width.BYTE),
	AX("reg_ax", Width.WORD),
	EAX("reg_eax", Width.DWORD),
	RAX("reg_rax", Width.QWORD),
	DX("reg_dx", Width.WORD),
	CL("reg_cl", Width.BYTE),
	ONE("unity"),
	REL8(null, Width.BYTE),
	REL16(null, Width.WORD),
	REL32(null, Width.DWORD),
	NONE("void", null),
	ST("fpureg", Width.TWORD),
	ST0("fpu0", Width.TWORD),
	MM("mmxreg", Width.QWORD),
	MMM64("mmxrm64"),
	X("xmmreg", Width.XWORD),
	XM8("xmmrm8"),
	XM16("xmmrm16"),
	XM32("xmmrm32"),
	XM64("xmmrm64"),
	XM128("xmmrm128"),
	XM256("xmmrm256"),
	X0("xmm0", Width.XWORD),
	Y("ymmreg", Width.YWORD),
	Z("zmmreg", Width.ZWORD),
	YM16("ymmrm16"),
	YM128("ymmrm128"),
	YM256("ymmrm256"),
	ZM16("zmmrm16"),
	ZM128("zmmrm128"),
	ZM256("zmmrm512"),
	VM32X("xmem32"),
	VM64X("xmem64"),
	VM32Y("ymem32"),
	VM64Y("ymem64"),
	VM32Z("zmem32"),
	VM64Z("zmem64"),
	BND("bndreg"),
	K("kreg"),
	KM8("krm8"),
	KM16("krm16"),
	KM32("krm32"),
	KM64("krm64"),
	T("tmmreg"),

}



val customMnemonics = setOf(
	"ENTER",
	"Jcc",
	"JMP",
	"CALL",
	"LOOP",
	"LOOPE",
	"LOOPNE",
	"LOOPZ",
	"LOOPNZ",
	"MOV",
	"PUSH",
	"XCHG",
	"POP",
)

