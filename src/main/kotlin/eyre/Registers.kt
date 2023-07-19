package eyre

import kotlin.random.Random



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

}



enum class RegType(val width: Width) {
	R8(Width.BYTE),
	R16(Width.WORD),
	R32(Width.DWORD),
	R64(Width.QWORD),
	ST(Width.TWORD),
	X(Width.XWORD),
	Y(Width.YWORD),
	Z(Width.ZWORD),
	MM(Width.QWORD),
	K(Width.WORD),
	T(Width.ZWORD),
	SEG(Width.DWORD),
	BND(Width.XWORD),
	CR(Width.QWORD),
	DR(Width.QWORD);
}



@JvmInline
value class OpMask(val value: Int) {
	operator fun contains(type: RegType) = (1 shl type.ordinal) and value != 0
	operator fun contains(reg: Reg)      = (1 shl reg.type.ordinal) and value != 0
	operator fun contains(width: Width)  = (1 shl width.ordinal) and value != 0
	operator fun contains(int: Int)      = (1 shl int) and value != 0
	operator fun plus(other: OpMask)     = OpMask(value or other.value)
}



enum class Reg(val type  : RegType, val index : Int) {

	AL  (RegType.R8, 0),
	CL  (RegType.R8, 1),
	DL  (RegType.R8, 2),
	BL  (RegType.R8, 3),
	AH  (RegType.R8, 4),
	BH  (RegType.R8, 5),
	CH  (RegType.R8, 6),
	DH  (RegType.R8, 7),
	R8B (RegType.R8, 8),
	R9B (RegType.R8, 9),
	R10B(RegType.R8, 10),
	R11B(RegType.R8, 11),
	R12B(RegType.R8, 12),
	R13B(RegType.R8, 13),
	R14B(RegType.R8, 14),
	R15B(RegType.R8, 15),

	SPL (RegType.R8, 4),
	BPL (RegType.R8, 5),
	SIL (RegType.R8, 6),
	DIL (RegType.R8, 7),

	AX  (RegType.R16, 0),
	CX  (RegType.R16, 1),
	DX  (RegType.R16, 2),
	BX  (RegType.R16, 3),
	SP  (RegType.R16, 4),
	BP  (RegType.R16, 5),
	SI  (RegType.R16, 6),
	DI  (RegType.R16, 7),
	R8W (RegType.R16, 8),
	R9W (RegType.R16, 9),
	R10W(RegType.R16, 10),
	R11W(RegType.R16, 11),
	R12W(RegType.R16, 12),
	R13W(RegType.R16, 13),
	R14W(RegType.R16, 14),
	R15W(RegType.R16, 15),

	EAX (RegType.R32, 0),
	ECX (RegType.R32, 1),
	EDX (RegType.R32, 2),
	EBX (RegType.R32, 3),
	ESP (RegType.R32, 4),
	EBP (RegType.R32, 5),
	ESI (RegType.R32, 6),
	EDI (RegType.R32, 7),
	R8D (RegType.R32, 8),
	R9D (RegType.R32, 9),
	R10D(RegType.R32, 10),
	R11D(RegType.R32, 11),
	R12D(RegType.R32, 12),
	R13D(RegType.R32, 13),
	R14D(RegType.R32, 14),
	R15D(RegType.R32, 15),

	RAX(RegType.R64, 0),
	RCX(RegType.R64, 1),
	RDX(RegType.R64, 2),
	RBX(RegType.R64, 3),
	RSP(RegType.R64, 4),
	RBP(RegType.R64, 5),
	RSI(RegType.R64, 6),
	RDI(RegType.R64, 7),
	R8 (RegType.R64, 8),
	R9 (RegType.R64, 9),
	R10(RegType.R64, 10),
	R11(RegType.R64, 11),
	R12(RegType.R64, 12),
	R13(RegType.R64, 13),
	R14(RegType.R64, 14),
	R15(RegType.R64, 15),

	ES(RegType.SEG, 0),
	CS(RegType.SEG, 1),
	SS(RegType.SEG, 2),
	DS(RegType.SEG, 3),
	FS(RegType.SEG, 4),
	GS(RegType.SEG, 5),

	ST0(RegType.ST, 0),
	ST1(RegType.ST, 1),
	ST2(RegType.ST, 2),
	ST3(RegType.ST, 3),
	ST4(RegType.ST, 4),
	ST5(RegType.ST, 5),
	ST6(RegType.ST, 6),
	ST7(RegType.ST, 7),

	MM0(RegType.MM, 0),
	MM1(RegType.MM, 1),
	MM2(RegType.MM, 2),
	MM3(RegType.MM, 3),
	MM4(RegType.MM, 4),
	MM5(RegType.MM, 5),
	MM6(RegType.MM, 6),
	MM7(RegType.MM, 7),
	
	XMM0 (RegType.X, 0),
	XMM1 (RegType.X, 1),
	XMM2 (RegType.X, 2),
	XMM3 (RegType.X, 3),
	XMM4 (RegType.X, 4),
	XMM5 (RegType.X, 5),
	XMM6 (RegType.X, 6),
	XMM7 (RegType.X, 7),
	XMM8 (RegType.X, 8),
	XMM9 (RegType.X, 9),
	XMM10(RegType.X, 10),
	XMM11(RegType.X, 11),
	XMM12(RegType.X, 12),
	XMM13(RegType.X, 13),
	XMM14(RegType.X, 14),
	XMM15(RegType.X, 15),
	XMM16(RegType.X, 16),
	XMM17(RegType.X, 17),
	XMM18(RegType.X, 18),
	XMM19(RegType.X, 19),
	XMM20(RegType.X, 20),
	XMM21(RegType.X, 21),
	XMM22(RegType.X, 22),
	XMM23(RegType.X, 23),
	XMM24(RegType.X, 24),
	XMM25(RegType.X, 25),
	XMM26(RegType.X, 26),
	XMM27(RegType.X, 27),
	XMM28(RegType.X, 28),
	XMM29(RegType.X, 29),
	XMM30(RegType.X, 30),
	XMM31(RegType.X, 31),

	YMM0 (RegType.Y, 0),
	YMM1 (RegType.Y, 1),
	YMM2 (RegType.Y, 2),
	YMM3 (RegType.Y, 3),
	YMM4 (RegType.Y, 4),
	YMM5 (RegType.Y, 5),
	YMM6 (RegType.Y, 6),
	YMM7 (RegType.Y, 7),
	YMM8 (RegType.Y, 8),
	YMM9 (RegType.Y, 9),
	YMM10(RegType.Y, 10),
	YMM11(RegType.Y, 11),
	YMM12(RegType.Y, 12),
	YMM13(RegType.Y, 13),
	YMM14(RegType.Y, 14),
	YMM15(RegType.Y, 15),
	YMM16(RegType.Y, 16),
	YMM17(RegType.Y, 17),
	YMM18(RegType.Y, 18),
	YMM19(RegType.Y, 19),
	YMM20(RegType.Y, 20),
	YMM21(RegType.Y, 21),
	YMM22(RegType.Y, 22),
	YMM23(RegType.Y, 23),
	YMM24(RegType.Y, 24),
	YMM25(RegType.Y, 25),
	YMM26(RegType.Y, 26),
	YMM27(RegType.Y, 27),
	YMM28(RegType.Y, 28),
	YMM29(RegType.Y, 29),
	YMM30(RegType.Y, 30),
	YMM31(RegType.Y, 31),

	ZMM0 (RegType.Z, 0),
	ZMM1 (RegType.Z, 1),
	ZMM2 (RegType.Z, 2),
	ZMM3 (RegType.Z, 3),
	ZMM4 (RegType.Z, 4),
	ZMM5 (RegType.Z, 5),
	ZMM6 (RegType.Z, 6),
	ZMM7 (RegType.Z, 7),
	ZMM8 (RegType.Z, 8),
	ZMM9 (RegType.Z, 9),
	ZMM10(RegType.Z, 10),
	ZMM11(RegType.Z, 11),
	ZMM12(RegType.Z, 12),
	ZMM13(RegType.Z, 13),
	ZMM14(RegType.Z, 14),
	ZMM15(RegType.Z, 15),
	ZMM16(RegType.Z, 16),
	ZMM17(RegType.Z, 17),
	ZMM18(RegType.Z, 18),
	ZMM19(RegType.Z, 19),
	ZMM20(RegType.Z, 20),
	ZMM21(RegType.Z, 21),
	ZMM22(RegType.Z, 22),
	ZMM23(RegType.Z, 23),
	ZMM24(RegType.Z, 24),
	ZMM25(RegType.Z, 25),
	ZMM26(RegType.Z, 26),
	ZMM27(RegType.Z, 27),
	ZMM28(RegType.Z, 28),
	ZMM29(RegType.Z, 29),
	ZMM30(RegType.Z, 30),
	ZMM31(RegType.Z, 31),

	K0(RegType.K, 0),
	K1(RegType.K, 1),
	K2(RegType.K, 2),
	K3(RegType.K, 3),
	K4(RegType.K, 4),
	K5(RegType.K, 5),
	K6(RegType.K, 6),
	K7(RegType.K, 7),

	CR0(RegType.CR, 0),
	CR1(RegType.CR, 1),
	CR2(RegType.CR, 2),
	CR3(RegType.CR, 3),
	CR4(RegType.CR, 4),
	CR5(RegType.CR, 5),
	CR6(RegType.CR, 6),
	CR7(RegType.CR, 7),
	CR8(RegType.CR, 8),

	DR0(RegType.DR, 0),
	DR1(RegType.DR, 1),
	DR2(RegType.DR, 2),
	DR3(RegType.DR, 3),
	DR4(RegType.DR, 4),
	DR5(RegType.DR, 5),
	DR6(RegType.DR, 6),
	DR7(RegType.DR, 7),

	BND0(RegType.BND, 0),
	BND1(RegType.BND, 1),
	BND2(RegType.BND, 2),
	BND3(RegType.BND, 3),

	TMM0(RegType.T, 0),
	TMM1(RegType.T, 1),
	TMM2(RegType.T, 2),
	TMM3(RegType.T, 3),
	TMM4(RegType.T, 4),
	TMM5(RegType.T, 5),
	TMM6(RegType.T, 6),
	TMM7(RegType.T, 7);

	val string = name.lowercase()

	val width    = type.width
	val value    = (index and 0b111)
	val rex      = (index shr 3) and 1
	val high     = (index shr 4) and 1
	val vexRex   = rex xor 1
	val vvvvValue = (value or (rex shl 3)).inv() and 0b1111
	val isR      = type.ordinal <= RegType.R64.ordinal
	val isA      = isR && value == 0 && rex == 0
	val rex8     = if(type == RegType.R8 && value in 4..7 && name.endsWith('L')) 1 else 0
	val noRex    = if(type == RegType.R8 && value in 4..7 && name.endsWith('H')) 1 else 0

	/** [ESP] or [RSP] */
	val isInvalidIndex = (type == RegType.R64 || type == RegType.R32) && index == 4
	/** [EBP] or [RBP] or [R13D] or [R13] */
	val isImperfectBase = (type == RegType.R64 || type == RegType.R32) && value == 5

	companion object {
		fun r8(index: Int) = entries[AL.ordinal + index]
		fun r8Rex(index: Int) = entries[AL.ordinal + 16 + index]
		fun r16(index: Int) = entries[AX.ordinal + index]
		fun r32(index: Int) = entries[EAX.ordinal + index]
		fun r64(index: Int = Random.nextInt(16)) = entries[RAX.ordinal + index]
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




/**
 *     00  4  type
 *     04  3  value
 *     04  3  value
 *     07  1  rex
 *     08  1  high
 *     09  1  rex8
 *     10  1  noRex
 *     11     size
 */
/*@JvmInline
value class Register(private val backing: Int) {

	constructor(type: Int, value: Int) : this(type and (value shl 8))

	val type    get() = (backing shr 0)  and 0xFF
	val ordinal get() = (backing shr 8)  and 0b11111
	val value   get() = (backing shr 8)  and 0b1111
	val rex     get() = (backing shr 10) and 1
	val high    get() = (backing shr 11) and 1
	val rex8    get() = (backing shr 12) and 1
	val noRex   get() = (backing shr 13) and 1

	val gpWidth get() = Width.entries[type]

	val isR   get() = type <= R64
	val isR8  get() = type == R8
	val isR16 get() = type == R16
	val isR32 get() = type == R32
	val isR64 get() = type == R64
	val isMM  get() = type == MM
	val isST  get() = type == ST
	val isX   get() = type == X
	val isY   get() = type == Y
	val isZ   get() = type == Z
	val isK   get() = type == K
	val isSEG get() = type == SEG
	val isCR  get() = type == CR
	val isDR  get() = type == DR
	val isBND get() = type == BND

	companion object {
		const val R8  = 0  // BYTE
		const val R16 = 1  // WORD
		const val R32 = 2  // DWORD
		const val R64 = 3  // QWORD
		const val ST  = 5  // TWORD
		const val X   = 6  // XWORD
		const val Y   = 7  // YWORD
		const val Z   = 8  // ZWORD
		const val MM  = 4
		const val K   = 9
		const val SEG = 10
		const val CR  = 11
		const val DR  = 12
		const val BND = 13
		const val TMM = 14

		val AL = Register(R8, 0)
		val CL = Register(R8, 1)
		val DL = Register(R8, 2)
		val BL = Register(R8, 3)
		val AH = Register(R8, 4)
		val CH = Register(R8, 5)
		val DH = Register(R8, 6)
		val BH = Register(R8, 7)
		val SIL = Register((R8 or (1 shl 9)), 7)
		val DIL = Register((R8 or (1 shl 10)), 8)

	}

}*/