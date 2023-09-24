package eyre



enum class Width(val string: String, val varString: String?, val bytes: Int) {

	BYTE("byte", "db", 1),
	WORD("word", "dw", 2),
	DWORD("dword", "dd", 4),
	QWORD("qword", "dq", 8),
	TWORD("tword", null, 10),
	XWORD("xword", null, 16),
	YWORD("yword", null, 32),
	ZWORD("zword", null, 64);

	private val min: Long = if(bytes > 8) 0 else -(1L shl ((bytes shl 3) - 1))
	private val max: Long = if(bytes > 8) 0 else (1L shl ((bytes shl 3) - 1)) - 1
	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max
	val nasmString = if(string == "xword") "oword" else string
	val immWidth get() = if(this == QWORD) DWORD else this
	val immLength = if(bytes > 4) 4 else bytes

	val isByte = bytes == 1
	val isWord = bytes == 2
	val isDword = bytes == 4
	val isQword = bytes == 8
	val isTword = bytes == 10
	val isXword = bytes == 16
	val isYword = bytes == 32
	val isZword = bytes == 64

}



enum class OpType(val gpWidth: Width = Width.BYTE) {
	R8(Width.BYTE),
	R16(Width.WORD),
	R32(Width.DWORD),
	R64(Width.QWORD),
	MM,
	ST,
	X,
	Y,
	Z,
	K,
	T,
	NONE,
	MEM,
	IMM,
	SEG,
	CR,
	DR,
	BND;
}



enum class Reg(val type: OpType, val index : Int) {

	AL(OpType.R8, 0), CL(OpType.R8, 1), DL(OpType.R8, 2), BL(OpType.R8, 3),
	AH(OpType.R8, 4), BH(OpType.R8, 5), CH(OpType.R8, 6), DH(OpType.R8, 7),
	R8B(OpType.R8, 8), R9B(OpType.R8, 9), R10B(OpType.R8, 10), R11B(OpType.R8, 11),
	R12B(OpType.R8, 12), R13B(OpType.R8, 13), R14B(OpType.R8, 14), R15B(OpType.R8, 15),
	SPL (OpType.R8, 4), BPL (OpType.R8, 5), SIL (OpType.R8, 6), DIL (OpType.R8, 7),

	AX(OpType.R16, 0), CX(OpType.R16, 1), DX(OpType.R16, 2), BX(OpType.R16, 3),
	SP(OpType.R16, 4), BP(OpType.R16, 5), SI(OpType.R16, 6), DI(OpType.R16, 7),
	R8W(OpType.R16, 8), R9W(OpType.R16, 9), R10W(OpType.R16, 10), R11W(OpType.R16, 11),
	R12W(OpType.R16, 12), R13W(OpType.R16, 13), R14W(OpType.R16, 14), R15W(OpType.R16, 15),

	EAX(OpType.R32, 0), ECX(OpType.R32, 1), EDX(OpType.R32, 2), EBX(OpType.R32, 3),
	ESP(OpType.R32, 4), EBP(OpType.R32, 5), ESI(OpType.R32, 6), EDI(OpType.R32, 7),
	R8D(OpType.R32, 8), R9D(OpType.R32, 9), R10D(OpType.R32, 10), R11D(OpType.R32, 11),
	R12D(OpType.R32, 12), R13D(OpType.R32, 13), R14D(OpType.R32, 14), R15D(OpType.R32, 15),

	RAX(OpType.R64, 0), RCX(OpType.R64, 1), RDX(OpType.R64, 2), RBX(OpType.R64, 3),
	RSP(OpType.R64, 4), RBP(OpType.R64, 5), RSI(OpType.R64, 6), RDI(OpType.R64, 7),
	R8(OpType.R64, 8), R9(OpType.R64, 9), R10(OpType.R64, 10), R11(OpType.R64, 11),
	R12(OpType.R64, 12), R13(OpType.R64, 13), R14(OpType.R64, 14), R15(OpType.R64, 15),

	MM0(OpType.MM, 0), MM1(OpType.MM, 1), MM2(OpType.MM, 2), MM3(OpType.MM, 3),
	MM4(OpType.MM, 4), MM5(OpType.MM, 5), MM6(OpType.MM, 6), MM7(OpType.MM, 7),

	ST0(OpType.ST, 0), ST1(OpType.ST, 1), ST2(OpType.ST, 2), ST3(OpType.ST, 3),
	ST4(OpType.ST, 4), ST5(OpType.ST, 5), ST6(OpType.ST, 6), ST7(OpType.ST, 7),

	XMM0(OpType.X, 0), XMM1(OpType.X, 1), XMM2(OpType.X, 2), XMM3(OpType.X, 3),
	XMM4(OpType.X, 4), XMM5(OpType.X, 5), XMM6(OpType.X, 6), XMM7(OpType.X, 7),
	XMM8(OpType.X, 8), XMM9(OpType.X, 9), XMM10(OpType.X, 10), XMM11(OpType.X, 11),
	XMM12(OpType.X, 12), XMM13(OpType.X, 13), XMM14(OpType.X, 14), XMM15(OpType.X, 15),
	XMM16(OpType.X, 16), XMM17(OpType.X, 17), XMM18(OpType.X, 18), XMM19(OpType.X, 19),
	XMM20(OpType.X, 20), XMM21(OpType.X, 21), XMM22(OpType.X, 22), XMM23(OpType.X, 23),
	XMM24(OpType.X, 24), XMM25(OpType.X, 25), XMM26(OpType.X, 26), XMM27(OpType.X, 27),
	XMM28(OpType.X, 28), XMM29(OpType.X, 29), XMM30(OpType.X, 30), XMM31(OpType.X, 31),

	YMM0(OpType.Y, 0), YMM1(OpType.Y, 1), YMM2(OpType.Y, 2), YMM3(OpType.Y, 3),
	YMM4(OpType.Y, 4), YMM5(OpType.Y, 5), YMM6(OpType.Y, 6), YMM7(OpType.Y, 7),
	YMM8(OpType.Y, 8), YMM9(OpType.Y, 9), YMM10(OpType.Y, 10), YMM11(OpType.Y, 11),
	YMM12(OpType.Y, 12), YMM13(OpType.Y, 13), YMM14(OpType.Y, 14), YMM15(OpType.Y, 15),
	YMM16(OpType.Y, 16), YMM17(OpType.Y, 17), YMM18(OpType.Y, 18), YMM19(OpType.Y, 19),
	YMM20(OpType.Y, 20), YMM21(OpType.Y, 21), YMM22(OpType.Y, 22), YMM23(OpType.Y, 23),
	YMM24(OpType.Y, 24), YMM25(OpType.Y, 25), YMM26(OpType.Y, 26), YMM27(OpType.Y, 27),
	YMM28(OpType.Y, 28), YMM29(OpType.Y, 29), YMM30(OpType.Y, 30), YMM31(OpType.Y, 31),

	ZMM0(OpType.Z, 0), ZMM1(OpType.Z, 1), ZMM2(OpType.Z, 2), ZMM3(OpType.Z, 3),
	ZMM4(OpType.Z, 4), ZMM5(OpType.Z, 5), ZMM6(OpType.Z, 6), ZMM7(OpType.Z, 7),
	ZMM8(OpType.Z, 8), ZMM9(OpType.Z, 9), ZMM10(OpType.Z, 10), ZMM11(OpType.Z, 11),
	ZMM12(OpType.Z, 12), ZMM13(OpType.Z, 13), ZMM14(OpType.Z, 14), ZMM15(OpType.Z, 15),
	ZMM16(OpType.Z, 16), ZMM17(OpType.Z, 17), ZMM18(OpType.Z, 18), ZMM19(OpType.Z, 19),
	ZMM20(OpType.Z, 20), ZMM21(OpType.Z, 21), ZMM22(OpType.Z, 22), ZMM23(OpType.Z, 23),
	ZMM24(OpType.Z, 24), ZMM25(OpType.Z, 25), ZMM26(OpType.Z, 26), ZMM27(OpType.Z, 27),
	ZMM28(OpType.Z, 28), ZMM29(OpType.Z, 29), ZMM30(OpType.Z, 30), ZMM31(OpType.Z, 31),

	K0(OpType.K, 0), K1(OpType.K, 1), K2(OpType.K, 2), K3(OpType.K, 3),
	K4(OpType.K, 4), K5(OpType.K, 5), K6(OpType.K, 6), K7(OpType.K, 7),

	TMM0(OpType.T, 0), TMM1(OpType.T, 1), TMM2(OpType.T, 2), TMM3(OpType.T, 3),
	TMM4(OpType.T, 4), TMM5(OpType.T, 5), TMM6(OpType.T, 6), TMM7(OpType.T, 7),

	ES(OpType.SEG, 0), CS(OpType.SEG, 1), SS(OpType.SEG, 2), DS(OpType.SEG, 3),
	FS(OpType.SEG, 4), GS(OpType.SEG, 5),

	CR0(OpType.CR, 0), CR1(OpType.CR, 1), CR2(OpType.CR, 2), CR3(OpType.CR, 3),
	CR4(OpType.CR, 4), CR5(OpType.CR, 5), CR6(OpType.CR, 6), CR7(OpType.CR, 7),
	CR8(OpType.CR, 8),

	DR0(OpType.DR, 0), DR1(OpType.DR, 1), DR2(OpType.DR, 2), DR3(OpType.DR, 3),
	DR4(OpType.DR, 4), DR5(OpType.DR, 5), DR6(OpType.DR, 6), DR7(OpType.DR, 7),

	BND0(OpType.BND, 0), BND1(OpType.BND, 1), BND2(OpType.BND, 2), BND3(OpType.BND, 3),

	NONE(OpType.NONE, 0);

	val width    = type.gpWidth
	val string   = name.lowercase()
	val value    = (index and 0b111)
	val rex      = (index shr 3) and 1
	val high     = (index shr 4) and 1
	val vexRex   = rex xor 1
	val vValue   = index.inv() and 0b1111
	val isR      = type.ordinal <= OpType.R64.ordinal
	val isV      = type.ordinal in OpType.X.ordinal..OpType.Z.ordinal
	val isA      = isR && index == 0
	val rex8     = if(type == OpType.R8 && value in 4..7 && name.endsWith('L')) 1 else 0
	val noRex    = if(type == OpType.R8 && value in 4..7 && name.endsWith('H')) 1 else 0

	/** [ESP] or [RSP] */
	val isInvalidIndex = (type == OpType.R64 || type == OpType.R32) && index == 4
	/** [EBP] or [RBP] or [R13D] or [R13] */
	val isImperfectBase = (type == OpType.R64 || type == OpType.R32) && value == 5

	companion object {
		fun r8(index: Int) = entries[AL.ordinal + index]
		fun r8Rex(index: Int) = entries[AL.ordinal + 16 + index]
		fun r16(index: Int) = entries[AX.ordinal + index]
		fun r32(index: Int) = entries[EAX.ordinal + index]
		fun r64(index: Int) = entries[RAX.ordinal + index]
		fun st(index: Int) = entries[ST0.ordinal + index]
		fun x(index: Int) = entries[XMM0.ordinal + index]
		fun y(index: Int) = entries[YMM0.ordinal + index]
		fun z(index: Int) = entries[ZMM0.ordinal + index]
		fun mm(index: Int) = entries[MM0.ordinal + index]
		fun cr(index: Int) = entries[CR0.ordinal + index]
		fun dr(index: Int) = entries[DR0.ordinal + index]
		fun k(index: Int) = entries[K0.ordinal + index]
		fun bnd(index: Int) = entries[BND0.ordinal + index]
		fun tmm(index: Int) = entries[TMM0.ordinal + index]
		fun seg(index: Int) = entries[ES.ordinal + index]
	}

}



/*
/**
 *     00  4  type
 *     04  3  value
 *     07  1  rex
 *     08  1  high
 *     09  1  rex8
 *     10  1  noRex
 *     11     size
 */
@JvmInline
value class Reg(private val backing: Int) {

	constructor(type: Int, value: Int) : 
		this(type or (value shl 8))
	
	constructor(type: Int, value: Int, rex8: Int, noRex: Int) :
		this(type or (value shl 8) or (rex8 shl 12) or (rex8 shl 13))

	val type    get() = (backing shr 0)  and 0xFF
	val ordinal get() = (backing shr 8)  and 0b11111
	val value   get() = (backing shr 8)  and 0b1111
	val rex     get() = (backing shr 10) and 1
	val high    get() = (backing shr 11) and 1
	val rex8    get() = (backing shr 12) and 1
	val noRex   get() = (backing shr 13) and 1

	val isR   get() = type <= R64
	val isR8  get() = type == R8
	val isR16 get() = type == R16
	val isR32 get() = type == R32
	val isR64 get() = type == R64
	val isST  get() = type == ST
	val isMM  get() = type == MM
	val isX   get() = type == X
	val isY   get() = type == Y
	val isZ   get() = type == Z
	val isK   get() = type == K
	val isT   get() = type == T
	val isSEG get() = type == SEG
	val isBND get() = type == BND
	val isCR  get() = type == CR
	val isDR  get() = type == DR
	
	/** ESP/RSP*/
	val invalidIndex get() = ordinal == 4
	/** EBP/RBP/R13D/R13*/
	val imperfectBase get() = value == 5
	val isA get() = isR && ordinal == 0
	val vexRex get() = rex xor 1
	val vvvvValue get() = ordinal.inv() and 0b1111
	val isV get() = type in X..Z

	override fun toString() = if(rex8 == 1)
		r8RexNames[value - 4]
	else
		names[type][ordinal]

	companion object {

		const val R8  = 0
		const val R16 = 1
		const val R32 = 2
		const val R64 = 3
		const val ST  = 4
		const val MM  = 5
		const val X   = 6
		const val Y   = 7
		const val Z   = 8
		const val K   = 9
		const val T   = 10
		const val SEG = 11
		const val BND = 12
		const val CR  = 13
		const val DR  = 14

		fun r8(index: Int)  = Reg(R8, index)
		fun r16(index: Int) = Reg(R16, index)
		fun r32(index: Int) = Reg(R32, index)
		fun r64(index: Int) = Reg(R64, index)
		fun st(index: Int)  = Reg(ST, index)
		fun x(index: Int)   = Reg(X, index)
		fun y(index: Int)   = Reg(Y, index)
		fun z(index: Int)   = Reg(Z, index)
		fun mm(index: Int)  = Reg(MM, index)
		fun cr(index: Int)  = Reg(CR, index)
		fun dr(index: Int)  = Reg(DR, index)
		fun k(index: Int)   = Reg(K, index)
		fun bnd(index: Int) = Reg(BND, index)
		fun tmm(index: Int) = Reg(T, index)
		fun seg(index: Int) = Reg(SEG, index)
		
		val r8Names = listOf(
			"al", "cl", "dl", "bl", "ah", "ch", "bh", "dh",
			"r8b", "r9b", "r10b", "r11b", "r12b", "r13b", "r14b", "r15b",
		)

		val r8RexNames = listOf("spl", "bpl", "sil", "dil")
		
		val r16Names = listOf(
			"ax", "cx", "dx", "bx", "bx", "sp", "bp", "si", "di",
			"r8w", "r9w", "r10w", "r11w", "r12w", "r13w", "r14w", "r15w"
		)

		val r32Names = listOf(
			"eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi",
			"r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d"
		)
		
		val r64Names = listOf(
			"rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi",
			"r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15"
		)

		val stNames = listOf("st0", "st1", "st2", "st3", "st4", "st5", "st6", "st7")

		val mmNames = listOf("mm0", "mm1", "mm2", "mm3", "mm4", "mm5", "mm6", "mm7")

		val xNames = listOf(
			"xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5", "xmm6", "xmm7",
			"xmm8", "xmm9", "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15",
			"xmm16", "xmm17", "xmm18", "xmm19", "xmm20", "xmm21", "xmm22", "xmm23",
			"xmm24", "xmm25", "xmm26", "xmm27", "xmm28", "xmm29", "xmm30", "xmm31",
		)

		val yNames = listOf(
			"ymm0", "ymm1", "ymm2", "ymm3", "ymm4", "ymm5", "ymm6", "ymm7",
			"ymm8", "ymm9", "ymm10", "ymm11", "ymm12", "ymm13", "ymm14", "ymm15",
			"ymm16", "ymm17", "ymm18", "ymm19", "ymm20", "ymm21", "ymm22", "ymm23",
			"ymm24", "ymm25", "ymm26", "ymm27", "ymm28", "ymm29", "ymm30", "ymm31",
		)

		val zNames = listOf(
			"zmm0", "zmm1", "zmm2", "zmm3", "zmm4", "zmm5", "zmm6", "zmm7",
			"zmm8", "zmm9", "zmm10", "zmm11", "zmm12", "zmm13", "zmm14", "zmm15",
			"zmm16", "zmm17", "zmm18", "zmm19", "zmm20", "zmm21", "zmm22", "zmm23",
			"zmm24", "zmm25", "zmm26", "zmm27", "zmm28", "zmm29", "zmm30", "zmm31",
		)

		val kNames = listOf("k0", "k1", "k2", "k3", "k4", "k5", "k6", "k7")

		val tNames = listOf("tmm0", "tmm1", "tmm2", "tmm3", "tmm4", "tmm5", "tmm6", "tmm7")

		val segNames = listOf("es", "cs", "ss", "ds", "fs", "gs")

		val bndNames = listOf("bnd0", "bnd1", "bnd2", "bnd3")

		val crNames = listOf("cr0", "cr1", "cr2", "cr3", "cr4", "cr5", "cr6", "cr7", "cr8")

		val drNames = listOf("dr0", "dr1", "dr2", "dr3", "dr4", "dr5", "dr6", "mm7")

		val names = listOf<List<String>>(
			r8Names,
			r16Names,
			r32Names,
			r64Names,
			stNames,
			mmNames,
			xNames,
			yNames,
			zNames,
			kNames,
			tNames,
			segNames,
			bndNames,
			crNames,
			drNames,
			drNames,
		)

	}

}*/