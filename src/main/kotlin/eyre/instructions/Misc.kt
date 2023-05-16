package eyre.instructions


class NasmLine(
	val lineNumber     : Int,
	val mnemonic       : String,
	val operandsString : String,
	val operands       : List<String>,
	val parts          : List<String>,
	val extras         : List<String>,
	val arch           : Arch?,
	val extension      : Extension?,
	val size           : Size?,
	val immWidth       : ImmWidth?,

) {

	override fun toString() = "$mnemonic $operandsString $parts $extras"

}



val Char.isHex get() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'



val ignoredParts = setOf(
	"hle",
	"nof3",
	"hlenl",
	"hlexr",
	"adf",
	"norexb",
	"norexx",
	"norexr",
	"norexw",
	"odf",
	"nohi",
	"nof3",
	"norep",
	"repe",
	"np",
	"iwdq"
)



enum class VsibPart {
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
	R8_I8,
	M16_R16_I16,
	M32_R32_I32,
	M64_R64_I32,
	M16_R16_CL,
	M32_R32_CL,
	M64_R64_CL,
}



val invalidExtras = setOf(
	"NOLONG",
	"NEVER",
	"UNDOC",
	"OBSOLETE",
	"AMD",
	"CYRIX",
	"LATEVEX",
	"OPT",
	"ND"
)



val ignoredExtras = setOf(
	"DEFAULT",
	"ANY",
	"VEX",
	"EVEX",
	"NOP",
	"HLE",
	"NOHLE",
	"PRIV",
	"SMM",
	"PROT",
	"LOCK",
	"LONG",
	"BND",
	"MIB",
	"SIB",
	"SIZE",
	"ANYSIZE",
)



object Maps {
	val sizes = Size.values().associateBy { it.name }
	val extras = Extra.values().associateBy { it.name }
	val arches = Arch.values().associateBy { it.name.trimStart('_') }
	val extensions = Extension.values().associateBy { it.name.trimStart('_') }
	val opParts = OpPart.values().associateBy { it.name.lowercase().replace('_', ',') }
	val immWidths = ImmWidth.values().associateBy { it.name.lowercase().replace('_', ',') }
	val vsibParts = VsibPart.values().associateBy { it.name.lowercase() }
}



enum class ImmWidth {
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



enum class Size(val sm: Boolean = false) {
	SB,
	SW,
	SD,
	SQ,
	SO,
	SY,
	SZ,
	SX,
	SM(true),
	SM2(true),
	AR0,
	AR1,
	AR2,
	SM2_SB_AR2(true),
	SD_AR1,
	SQ_AR1,
	SB_AR2,
	SB_AR1,
	SB_SM(true);
}



enum class OpSize {
	SB,
	SW,
	SD,
	SQ,
	SO,
	SY,
	SZ,
	SX;
}
enum class Extra {
	SM,
	SM2,
	SB,
	SW,
	SD,
	SQ,
	SO,
	SY,
	SZ,
	SX,
	AR0,
	AR1,
	AR2;
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