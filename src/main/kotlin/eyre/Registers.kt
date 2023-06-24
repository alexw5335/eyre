package eyre

import eyre.util.bin44



@JvmInline
value class WidthAndMask(private val value: Int) {
	val width   get() = Width.values[(value and 0xFF).countTrailingZeroBits()]
	val mask    get() = OpMask(value shr 8)
	val isValid get() = (value and 0xFF) in mask
}



enum class Width(val string: String, val varString: String?, val bytes: Int) {

	BYTE("byte", "db", 1),
	WORD("word", "dw", 2),
	DWORD("dword", "dd", 4),
	QWORD("qword", "dq", 8),
	TWORD("tword", null, 10),
	XWORD("xword", null, 16),
	YWORD("yword", null, 32),
	ZWORD("zword", null, 64);

	// Only for BYTE, WORD, DWORD, and QWORD
	private val min: Long = -(1L shl ((bytes shl 3) - 1))
	private val max: Long = (1L shl ((bytes shl 3) - 1)) - 1
	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

	companion object { val values = values() }

}



enum class RegType {
	R8,
	R16,
	R32,
	R64,
	ST,
	X,
	Y,
	Z,
	MM,
	K,
	SEG,
	BND,
	CR,
	DR;
}



@JvmInline
value class OpMask(val value: Int) {

	val isEmpty    get() = value == 0
	val isNotEmpty get() = value != 0
	val highest    get() = Width.values[32 - value.countLeadingZeroBits()]
	val lowest     get() = Width.values[value.countLeadingZeroBits()]
	val isSingle   get() = value.countOneBits() == 1
	val count      get() = value.countOneBits()

	operator fun contains(type: RegType) = (1 shl type.ordinal) and value != 0
	operator fun contains(reg: Reg) = (1 shl reg.type.ordinal) and value != 0
	operator fun contains(width: Width) = (1 shl width.ordinal) and value != 0
	operator fun contains(int: Int) = (1 shl int) and value != 0

	operator fun plus(other: OpMask) = OpMask(value or other.value)

	companion object {
		val NONE  = OpMask(0)   // NONE
		val BYTE  = OpMask(1)   // R8  M8  I8  REL8
		val WORD  = OpMask(2)   // R16 M16 I16 REL16
		val DWORD = OpMask(4)   // R32 M32 I32 REL32
		val QWORD = OpMask(8)   // R64 M64 I32
		val TWORD = OpMask(16)  // ST  M80
		val XWORD = OpMask(32)  // XMM M128
		val YWORD = OpMask(64)  // YMM M256
		val ZWORD = OpMask(128) // ZMM M512

		val R0111 = OpMask(0b0111)
		val R1000 = OpMask(0b1000)
		val R1111 = OpMask(0b1111)
		val R1100 = OpMask(0b1100)
		val R1110 = OpMask(0b1110)
	}

	override fun toString() = value.bin44

	inline fun forEachWidth(block: (Width) -> Unit) {
		for(width in Width.values)
			if(width in this)
				block(width)
	}

}


/**
 * - Bits 0-7: type
 * - Bits 8-11: value
 * - Bits 12-12: rex8
 * - Bits 13-13: noRex
 * - rex =
 */
@JvmInline
value class Register(private val backing: Int) {

	constructor(type: Int, value: Int) : this(type and (value shl 8))

	val type    get() = (backing shr 0)  and 0xFF
	val ordinal get() = (backing shr 8)  and 0b11111
	val value   get() = (backing shr 8)  and 0b1111
	val rex     get() = (backing shr 10) and 1
	val high    get() = (backing shr 11) and 1
	val rex8    get() = (backing shr 12) and 1
	val noRex   get() = (backing shr 13) and 1

	val isR get() = type <= R64
	val isR8 get() = type == R8
	val isR16 get() = type == R16
	val isR32 get() = type == R32
	val isR64 get() = type == R64
	val isMM get() = type == MM
	val isST get() = type == ST
	val isX get() = type == X
	val isY get() = type == Y
	val isZ get() = type == Z
	val isK get() = type == K
	val isSEG get() = type == SEG
	val isCR get() = type == CR
	val isDR get() = type == DR
	val isBND get() = type == BND


	companion object {
		const val R8 = 0
		const val R16 = 1
		const val R32 = 2
		const val R64 = 3
		const val MM = 4
		const val ST = 5
		const val X = 6
		const val Y = 7
		const val Z = 8
		const val K = 9
		const val SEG = 10
		const val CR = 11
		const val DR = 12
		const val BND = 13

		val CL = Register(R8, 1)
		val AL = Register(R8, 0)
		val AX = Register(R16, 0)
		val EAX = Register(R32, 0)
		val RAX = Register(R64, 0)
	}

}



enum class Reg(
	val type  : RegType,
	val width : Width,
	val value : Int,
	val rex   : Int,
	val high  : Int,
	val rex8  : Int = 0,
	val noRex : Int = 0
) {

	RAX(RegType.R64, Width.QWORD, 0, 0, 0),
	RCX(RegType.R64, Width.QWORD, 1, 0, 0),
	RDX(RegType.R64, Width.QWORD, 2, 0, 0),
	RBX(RegType.R64, Width.QWORD, 3, 0, 0),
	RSP(RegType.R64, Width.QWORD, 4, 0, 0),
	RBP(RegType.R64, Width.QWORD, 5, 0, 0),
	RSI(RegType.R64, Width.QWORD, 6, 0, 0),
	RDI(RegType.R64, Width.QWORD, 7, 0, 0),
	R8 (RegType.R64, Width.QWORD, 0, 1, 0),
	R9 (RegType.R64, Width.QWORD, 1, 1, 0),
	R10(RegType.R64, Width.QWORD, 2, 1, 0),
	R11(RegType.R64, Width.QWORD, 3, 1, 0),
	R12(RegType.R64, Width.QWORD, 4, 1, 0),
	R13(RegType.R64, Width.QWORD, 5, 1, 0),
	R14(RegType.R64, Width.QWORD, 6, 1, 0),
	R15(RegType.R64, Width.QWORD, 7, 1, 0),

	EAX (RegType.R32, Width.DWORD, 0, 0, 0),
	ECX (RegType.R32, Width.DWORD, 1, 0, 0),
	EDX (RegType.R32, Width.DWORD, 2, 0, 0),
	EBX (RegType.R32, Width.DWORD, 3, 0, 0),
	ESP (RegType.R32, Width.DWORD, 4, 0, 0),
	EBP (RegType.R32, Width.DWORD, 5, 0, 0),
	ESI (RegType.R32, Width.DWORD, 6, 0, 0),
	EDI (RegType.R32, Width.DWORD, 7, 0, 0),
	R8D (RegType.R32, Width.DWORD, 0, 1, 0),
	R9D (RegType.R32, Width.DWORD, 1, 1, 0),
	R10D(RegType.R32, Width.DWORD, 2, 1, 0),
	R11D(RegType.R32, Width.DWORD, 3, 1, 0),
	R12D(RegType.R32, Width.DWORD, 4, 1, 0),
	R13D(RegType.R32, Width.DWORD, 5, 1, 0),
	R14D(RegType.R32, Width.DWORD, 6, 1, 0),
	R15D(RegType.R32, Width.DWORD, 7, 1, 0),

	AX  (RegType.R16, Width.WORD, 0, 0, 0),
	CX  (RegType.R16, Width.WORD, 1, 0, 0),
	DX  (RegType.R16, Width.WORD, 2, 0, 0),
	BX  (RegType.R16, Width.WORD, 3, 0, 0),
	SP  (RegType.R16, Width.WORD, 4, 0, 0),
	BP  (RegType.R16, Width.WORD, 5, 0, 0),
	SI  (RegType.R16, Width.WORD, 6, 0, 0),
	DI  (RegType.R16, Width.WORD, 7, 0, 0),
	R8W (RegType.R16, Width.WORD, 0, 1, 0),
	R9W (RegType.R16, Width.WORD, 1, 1, 0),
	R10W(RegType.R16, Width.WORD, 2, 1, 0),
	R11W(RegType.R16, Width.WORD, 3, 1, 0),
	R12W(RegType.R16, Width.WORD, 4, 1, 0),
	R13W(RegType.R16, Width.WORD, 5, 1, 0),
	R14W(RegType.R16, Width.WORD, 6, 1, 0),
	R15W(RegType.R16, Width.WORD, 7, 1, 0),

	AL  (RegType.R8, Width.BYTE, 0, 0, 0),
	CL  (RegType.R8, Width.BYTE, 1, 0, 0),
	DL  (RegType.R8, Width.BYTE, 2, 0, 0),
	BL  (RegType.R8, Width.BYTE, 3, 0, 0),
	AH  (RegType.R8, Width.BYTE, 4, 0, 0, noRex = 1),
	BH  (RegType.R8, Width.BYTE, 5, 0, 0, noRex = 1),
	CH  (RegType.R8, Width.BYTE, 6, 0, 0, noRex = 1),
	DH  (RegType.R8, Width.BYTE, 7, 0, 0, noRex = 1),
	SPL (RegType.R8, Width.BYTE, 4, 0, 0, rex8 = 1),
	BPL (RegType.R8, Width.BYTE, 5, 0, 0, rex8 = 1),
	SIL (RegType.R8, Width.BYTE, 6, 0, 0, rex8 = 1),
	DIL (RegType.R8, Width.BYTE, 7, 0, 0, rex8 = 1),
	R8B (RegType.R8, Width.BYTE, 0, 1, 0),
	R9B (RegType.R8, Width.BYTE, 1, 1, 0),
	R10B(RegType.R8, Width.BYTE, 2, 1, 0),
	R11B(RegType.R8, Width.BYTE, 3, 1, 0),
	R12B(RegType.R8, Width.BYTE, 4, 1, 0),
	R13B(RegType.R8, Width.BYTE, 5, 1, 0),
	R14B(RegType.R8, Width.BYTE, 6, 1, 0),
	R15B(RegType.R8, Width.BYTE, 7, 1, 0),
	


	ES(RegType.SEG, Width.WORD, 0, 0, 0),
	CS(RegType.SEG, Width.WORD, 1, 0, 0),
	SS(RegType.SEG, Width.WORD, 2, 0, 0),
	DS(RegType.SEG, Width.WORD, 3, 0, 0),
	FS(RegType.SEG, Width.WORD, 4, 0, 0),
	GS(RegType.SEG, Width.WORD, 5, 0, 0),

	ST0(RegType.ST, Width.TWORD, 0, 0, 0),
	ST1(RegType.ST, Width.TWORD, 1, 0, 0),
	ST2(RegType.ST, Width.TWORD, 2, 0, 0),
	ST3(RegType.ST, Width.TWORD, 3, 0, 0),
	ST4(RegType.ST, Width.TWORD, 4, 0, 0),
	ST5(RegType.ST, Width.TWORD, 5, 0, 0),
	ST6(RegType.ST, Width.TWORD, 6, 0, 0),
	ST7(RegType.ST, Width.TWORD, 7, 0, 0),

	MM0(RegType.MM, Width.QWORD, 0, 0, 0),
	MM1(RegType.MM, Width.QWORD, 1, 0, 0),
	MM2(RegType.MM, Width.QWORD, 2, 0, 0),
	MM3(RegType.MM, Width.QWORD, 3, 0, 0),
	MM4(RegType.MM, Width.QWORD, 4, 0, 0),
	MM5(RegType.MM, Width.QWORD, 5, 0, 0),
	MM6(RegType.MM, Width.QWORD, 6, 0, 0),
	MM7(RegType.MM, Width.QWORD, 7, 0, 0),
	
	XMM0 (RegType.X, Width.XWORD, 0, 0, 0),
	XMM1 (RegType.X, Width.XWORD, 1, 0, 0),
	XMM2 (RegType.X, Width.XWORD, 2, 0, 0),
	XMM3 (RegType.X, Width.XWORD, 3, 0, 0),
	XMM4 (RegType.X, Width.XWORD, 4, 0, 0),
	XMM5 (RegType.X, Width.XWORD, 5, 0, 0),
	XMM6 (RegType.X, Width.XWORD, 6, 0, 0),
	XMM7 (RegType.X, Width.XWORD, 7, 0, 0),
	XMM8 (RegType.X, Width.XWORD, 0, 1, 0),
	XMM9 (RegType.X, Width.XWORD, 1, 1, 0),
	XMM10(RegType.X, Width.XWORD, 2, 1, 0),
	XMM11(RegType.X, Width.XWORD, 3, 1, 0),
	XMM12(RegType.X, Width.XWORD, 4, 1, 0),
	XMM13(RegType.X, Width.XWORD, 5, 1, 0),
	XMM14(RegType.X, Width.XWORD, 6, 1, 0),
	XMM15(RegType.X, Width.XWORD, 7, 1, 0),
	XMM16(RegType.X, Width.XWORD, 0, 0, 1),
	XMM17(RegType.X, Width.XWORD, 1, 0, 1),
	XMM18(RegType.X, Width.XWORD, 2, 0, 1),
	XMM19(RegType.X, Width.XWORD, 3, 0, 1),
	XMM20(RegType.X, Width.XWORD, 4, 0, 1),
	XMM21(RegType.X, Width.XWORD, 5, 0, 1),
	XMM22(RegType.X, Width.XWORD, 6, 0, 1),
	XMM23(RegType.X, Width.XWORD, 7, 0, 1),
	XMM24(RegType.X, Width.XWORD, 0, 1, 1),
	XMM25(RegType.X, Width.XWORD, 1, 1, 1),
	XMM26(RegType.X, Width.XWORD, 2, 1, 1),
	XMM27(RegType.X, Width.XWORD, 3, 1, 1),
	XMM28(RegType.X, Width.XWORD, 4, 1, 1),
	XMM29(RegType.X, Width.XWORD, 5, 1, 1),
	XMM30(RegType.X, Width.XWORD, 6, 1, 1),
	XMM31(RegType.X, Width.XWORD, 7, 1, 1),

	YMM0 (RegType.Y, Width.YWORD, 0, 0, 0),
	YMM1 (RegType.Y, Width.YWORD, 1, 0, 0),
	YMM2 (RegType.Y, Width.YWORD, 2, 0, 0),
	YMM3 (RegType.Y, Width.YWORD, 3, 0, 0),
	YMM4 (RegType.Y, Width.YWORD, 4, 0, 0),
	YMM5 (RegType.Y, Width.YWORD, 5, 0, 0),
	YMM6 (RegType.Y, Width.YWORD, 6, 0, 0),
	YMM7 (RegType.Y, Width.YWORD, 7, 0, 0),
	YMM8 (RegType.Y, Width.YWORD, 0, 1, 0),
	YMM9 (RegType.Y, Width.YWORD, 1, 1, 0),
	YMM10(RegType.Y, Width.YWORD, 2, 1, 0),
	YMM11(RegType.Y, Width.YWORD, 3, 1, 0),
	YMM12(RegType.Y, Width.YWORD, 4, 1, 0),
	YMM13(RegType.Y, Width.YWORD, 5, 1, 0),
	YMM14(RegType.Y, Width.YWORD, 6, 1, 0),
	YMM15(RegType.Y, Width.YWORD, 7, 1, 0),
	YMM16(RegType.Y, Width.YWORD, 0, 0, 1),
	YMM17(RegType.Y, Width.YWORD, 1, 0, 1),
	YMM18(RegType.Y, Width.YWORD, 2, 0, 1),
	YMM19(RegType.Y, Width.YWORD, 3, 0, 1),
	YMM20(RegType.Y, Width.YWORD, 4, 0, 1),
	YMM21(RegType.Y, Width.YWORD, 5, 0, 1),
	YMM22(RegType.Y, Width.YWORD, 6, 0, 1),
	YMM23(RegType.Y, Width.YWORD, 7, 0, 1),
	YMM24(RegType.Y, Width.YWORD, 0, 1, 1),
	YMM25(RegType.Y, Width.YWORD, 1, 1, 1),
	YMM26(RegType.Y, Width.YWORD, 2, 1, 1),
	YMM27(RegType.Y, Width.YWORD, 3, 1, 1),
	YMM28(RegType.Y, Width.YWORD, 4, 1, 1),
	YMM29(RegType.Y, Width.YWORD, 5, 1, 1),
	YMM30(RegType.Y, Width.YWORD, 6, 1, 1),
	YMM31(RegType.Y, Width.YWORD, 7, 1, 1),

	ZMM0 (RegType.Z, Width.ZWORD, 0, 0, 0),
	ZMM1 (RegType.Z, Width.ZWORD, 1, 0, 0),
	ZMM2 (RegType.Z, Width.ZWORD, 2, 0, 0),
	ZMM3 (RegType.Z, Width.ZWORD, 3, 0, 0),
	ZMM4 (RegType.Z, Width.ZWORD, 4, 0, 0),
	ZMM5 (RegType.Z, Width.ZWORD, 5, 0, 0),
	ZMM6 (RegType.Z, Width.ZWORD, 6, 0, 0),
	ZMM7 (RegType.Z, Width.ZWORD, 7, 0, 0),
	ZMM8 (RegType.Z, Width.ZWORD, 0, 1, 0),
	ZMM9 (RegType.Z, Width.ZWORD, 1, 1, 0),
	ZMM10(RegType.Z, Width.ZWORD, 2, 1, 0),
	ZMM11(RegType.Z, Width.ZWORD, 3, 1, 0),
	ZMM12(RegType.Z, Width.ZWORD, 4, 1, 0),
	ZMM13(RegType.Z, Width.ZWORD, 5, 1, 0),
	ZMM14(RegType.Z, Width.ZWORD, 6, 1, 0),
	ZMM15(RegType.Z, Width.ZWORD, 7, 1, 0),
	ZMM16(RegType.Z, Width.ZWORD, 0, 0, 1),
	ZMM17(RegType.Z, Width.ZWORD, 1, 0, 1),
	ZMM18(RegType.Z, Width.ZWORD, 2, 0, 1),
	ZMM19(RegType.Z, Width.ZWORD, 3, 0, 1),
	ZMM20(RegType.Z, Width.ZWORD, 4, 0, 1),
	ZMM21(RegType.Z, Width.ZWORD, 5, 0, 1),
	ZMM22(RegType.Z, Width.ZWORD, 6, 0, 1),
	ZMM23(RegType.Z, Width.ZWORD, 7, 0, 1),
	ZMM24(RegType.Z, Width.ZWORD, 0, 1, 1),
	ZMM25(RegType.Z, Width.ZWORD, 1, 1, 1),
	ZMM26(RegType.Z, Width.ZWORD, 2, 1, 1),
	ZMM27(RegType.Z, Width.ZWORD, 3, 1, 1),
	ZMM28(RegType.Z, Width.ZWORD, 4, 1, 1),
	ZMM29(RegType.Z, Width.ZWORD, 5, 1, 1),
	ZMM30(RegType.Z, Width.ZWORD, 6, 1, 1),
	ZMM31(RegType.Z, Width.ZWORD, 7, 1, 1),

	K0(RegType.K, Width.DWORD, 0, 0, 0),
	K1(RegType.K, Width.DWORD, 1, 0, 0),
	K2(RegType.K, Width.DWORD, 2, 0, 0),
	K3(RegType.K, Width.DWORD, 3, 0, 0),
	K4(RegType.K, Width.DWORD, 4, 0, 0),
	K5(RegType.K, Width.DWORD, 5, 0, 0),
	K6(RegType.K, Width.DWORD, 6, 0, 0),
	K7(RegType.K, Width.DWORD, 7, 0, 0),

	CR0(RegType.CR, Width.QWORD, 0, 0, 0),
	CR1(RegType.CR, Width.QWORD, 1, 0, 0),
	CR2(RegType.CR, Width.QWORD, 2, 0, 0),
	CR3(RegType.CR, Width.QWORD, 3, 0, 0),
	CR4(RegType.CR, Width.QWORD, 4, 0, 0),
	CR5(RegType.CR, Width.QWORD, 5, 0, 0),
	CR6(RegType.CR, Width.QWORD, 6, 0, 0),
	CR7(RegType.CR, Width.QWORD, 7, 0, 0),
	CR8(RegType.CR, Width.QWORD, 0, 1, 0),

	DR0(RegType.DR, Width.QWORD, 0, 0, 0),
	DR1(RegType.DR, Width.QWORD, 1, 0, 0),
	DR2(RegType.DR, Width.QWORD, 2, 0, 0),
	DR3(RegType.DR, Width.QWORD, 3, 0, 0),
	DR4(RegType.DR, Width.QWORD, 4, 0, 0),
	DR5(RegType.DR, Width.QWORD, 5, 0, 0),
	DR6(RegType.DR, Width.QWORD, 6, 0, 0),
	DR7(RegType.DR, Width.QWORD, 7, 0, 0),

	BND0(RegType.BND, Width.XWORD, 0, 0, 0),
	BND1(RegType.BND, Width.XWORD, 1, 0, 0),
	BND2(RegType.BND, Width.XWORD, 2, 0, 0),
	BND3(RegType.BND, Width.XWORD, 3, 0, 0);

	val string = name.lowercase()

	val isR = type in OpMask.R1111
	val isA = isR && value == 0 && rex == 0
	val isST = type == RegType.ST
}