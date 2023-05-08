package eyre



enum class RegType {
	GP, SEG, FPU, MMX, SSE, MASK;
}



@JvmInline
value class Widths2(val value: Int) {
	companion object {
		val GP8      = Widths2(0b0001)
		val GP16     = Widths2(0b0010)
		val GP32     = Widths2(0b0100)
		val GP64     = Widths2(0b1000)
		val GP       = Widths2(0b1111)
		val GP816    = Widths2(0b0011)
		val GP81632  = Widths2(0b0111)
		val GP1664   = Widths2(0b1010)
		val GP163264 = Widths2(0b1110)
		val SSE      = Widths2(0b0111_0000)
		val MMX      = Widths2(0b1000_0000)
	}
}



enum class Reg(
	val type: RegType,
	val width: Width,
	val value: Int,
	val rex: Int,
	val high: Int,
	val isRex8: Boolean = false,
	val noRex: Boolean = false
) {

	RAX(RegType.GP, Width.QWORD, 0, 0, 0),
	RCX(RegType.GP, Width.QWORD, 1, 0, 0),
	RDX(RegType.GP, Width.QWORD, 2, 0, 0),
	RBX(RegType.GP, Width.QWORD, 3, 0, 0),
	RSP(RegType.GP, Width.QWORD, 4, 0, 0),
	RBP(RegType.GP, Width.QWORD, 5, 0, 0),
	RSI(RegType.GP, Width.QWORD, 6, 0, 0),
	RDI(RegType.GP, Width.QWORD, 7, 0, 0),
	R8 (RegType.GP, Width.QWORD, 0, 1, 0),
	R9 (RegType.GP, Width.QWORD, 1, 1, 0),
	R10(RegType.GP, Width.QWORD, 2, 1, 0),
	R11(RegType.GP, Width.QWORD, 3, 1, 0),
	R12(RegType.GP, Width.QWORD, 4, 1, 0),
	R13(RegType.GP, Width.QWORD, 5, 1, 0),
	R14(RegType.GP, Width.QWORD, 6, 1, 0),
	R15(RegType.GP, Width.QWORD, 7, 1, 0),

	EAX (RegType.GP, Width.DWORD, 0, 0, 0),
	ECX (RegType.GP, Width.DWORD, 1, 0, 0),
	EDX (RegType.GP, Width.DWORD, 2, 0, 0),
	EBX (RegType.GP, Width.DWORD, 3, 0, 0),
	ESP (RegType.GP, Width.DWORD, 4, 0, 0),
	EBP (RegType.GP, Width.DWORD, 5, 0, 0),
	ESI (RegType.GP, Width.DWORD, 6, 0, 0),
	EDI (RegType.GP, Width.DWORD, 7, 0, 0),
	R8D (RegType.GP, Width.DWORD, 0, 1, 0),
	R9D (RegType.GP, Width.DWORD, 1, 1, 0),
	R10D(RegType.GP, Width.DWORD, 2, 1, 0),
	R11D(RegType.GP, Width.DWORD, 3, 1, 0),
	R12D(RegType.GP, Width.DWORD, 4, 1, 0),
	R13D(RegType.GP, Width.DWORD, 5, 1, 0),
	R14D(RegType.GP, Width.DWORD, 6, 1, 0),
	R15D(RegType.GP, Width.DWORD, 7, 1, 0),

	AX  (RegType.GP, Width.WORD, 0, 0, 0),
	CX  (RegType.GP, Width.WORD, 1, 0, 0),
	DX  (RegType.GP, Width.WORD, 2, 0, 0),
	BX  (RegType.GP, Width.WORD, 3, 0, 0),
	SP  (RegType.GP, Width.WORD, 4, 0, 0),
	BP  (RegType.GP, Width.WORD, 5, 0, 0),
	SI  (RegType.GP, Width.WORD, 6, 0, 0),
	DI  (RegType.GP, Width.WORD, 7, 0, 0),
	R8W (RegType.GP, Width.WORD, 0, 1, 0),
	R9W (RegType.GP, Width.WORD, 1, 1, 0),
	R10W(RegType.GP, Width.WORD, 2, 1, 0),
	R11W(RegType.GP, Width.WORD, 3, 1, 0),
	R12W(RegType.GP, Width.WORD, 4, 1, 0),
	R13W(RegType.GP, Width.WORD, 5, 1, 0),
	R14W(RegType.GP, Width.WORD, 6, 1, 0),
	R15W(RegType.GP, Width.WORD, 7, 1, 0),

	AL  (RegType.GP, Width.BYTE, 0, 0, 0),
	CL  (RegType.GP, Width.BYTE, 1, 0, 0),
	DL  (RegType.GP, Width.BYTE, 2, 0, 0),
	BL  (RegType.GP, Width.BYTE, 3, 0, 0),
	AH  (RegType.GP, Width.BYTE, 4, 0, 0, isRex8 = true),
	BH  (RegType.GP, Width.BYTE, 5, 0, 0, isRex8 = true),
	CH  (RegType.GP, Width.BYTE, 6, 0, 0, isRex8 = true),
	DH  (RegType.GP, Width.BYTE, 7, 0, 0, isRex8 = true),
	R8B (RegType.GP, Width.BYTE, 0, 1, 0),
	R9B (RegType.GP, Width.BYTE, 1, 1, 0),
	R10B(RegType.GP, Width.BYTE, 2, 1, 0),
	R11B(RegType.GP, Width.BYTE, 3, 1, 0),
	R12B(RegType.GP, Width.BYTE, 4, 1, 0),
	R13B(RegType.GP, Width.BYTE, 5, 1, 0),
	R14B(RegType.GP, Width.BYTE, 6, 1, 0),
	R15B(RegType.GP, Width.BYTE, 7, 1, 0),
	
	SPL(RegType.GP, Width.BYTE, 0, 1, 0, noRex = true),
	BPL(RegType.GP, Width.BYTE, 1, 1, 0, noRex = true),
	SIL(RegType.GP, Width.BYTE, 2, 1, 0, noRex = true),
	DIL(RegType.GP, Width.BYTE, 3, 1, 0, noRex = true),

	FS(RegType.SEG, Width.WORD, 0, 0, 0),
	GS(RegType.SEG, Width.WORD, 0, 0, 0),
	
	MM0(RegType.MMX, Width.QWORD, 0, 0, 0),
	MM1(RegType.MMX, Width.QWORD, 1, 0, 0),
	MM2(RegType.MMX, Width.QWORD, 2, 0, 0),
	MM3(RegType.MMX, Width.QWORD, 3, 0, 0),
	MM4(RegType.MMX, Width.QWORD, 4, 0, 0),
	MM5(RegType.MMX, Width.QWORD, 5, 0, 0),
	MM6(RegType.MMX, Width.QWORD, 6, 0, 0),
	MM7(RegType.MMX, Width.QWORD, 7, 0, 0),
	
	XMM0 (RegType.SSE, Width.XWORD, 0, 0, 0),
	XMM1 (RegType.SSE, Width.XWORD, 1, 0, 0),
	XMM2 (RegType.SSE, Width.XWORD, 2, 0, 0),
	XMM3 (RegType.SSE, Width.XWORD, 3, 0, 0),
	XMM4 (RegType.SSE, Width.XWORD, 4, 0, 0),
	XMM5 (RegType.SSE, Width.XWORD, 5, 0, 0),
	XMM6 (RegType.SSE, Width.XWORD, 6, 0, 0),
	XMM7 (RegType.SSE, Width.XWORD, 7, 0, 0),
	XMM8 (RegType.SSE, Width.XWORD, 0, 1, 0),
	XMM9 (RegType.SSE, Width.XWORD, 1, 1, 0),
	XMM10(RegType.SSE, Width.XWORD, 2, 1, 0),
	XMM11(RegType.SSE, Width.XWORD, 3, 1, 0),
	XMM12(RegType.SSE, Width.XWORD, 4, 1, 0),
	XMM13(RegType.SSE, Width.XWORD, 5, 1, 0),
	XMM14(RegType.SSE, Width.XWORD, 6, 1, 0),
	XMM15(RegType.SSE, Width.XWORD, 7, 1, 0),
	XMM16(RegType.SSE, Width.XWORD, 0, 0, 1),
	XMM17(RegType.SSE, Width.XWORD, 1, 0, 1),
	XMM18(RegType.SSE, Width.XWORD, 2, 0, 1),
	XMM19(RegType.SSE, Width.XWORD, 3, 0, 1),
	XMM20(RegType.SSE, Width.XWORD, 4, 0, 1),
	XMM21(RegType.SSE, Width.XWORD, 5, 0, 1),
	XMM22(RegType.SSE, Width.XWORD, 6, 0, 1),
	XMM23(RegType.SSE, Width.XWORD, 7, 0, 1),
	XMM24(RegType.SSE, Width.XWORD, 0, 1, 1),
	XMM25(RegType.SSE, Width.XWORD, 1, 1, 1),
	XMM26(RegType.SSE, Width.XWORD, 2, 1, 1),
	XMM27(RegType.SSE, Width.XWORD, 3, 1, 1),
	XMM28(RegType.SSE, Width.XWORD, 4, 1, 1),
	XMM29(RegType.SSE, Width.XWORD, 5, 1, 1),
	XMM30(RegType.SSE, Width.XWORD, 6, 1, 1),
	XMM31(RegType.SSE, Width.XWORD, 7, 1, 1),

	YMM0 (RegType.SSE, Width.YWORD, 0, 0, 0),
	YMM1 (RegType.SSE, Width.YWORD, 1, 0, 0),
	YMM2 (RegType.SSE, Width.YWORD, 2, 0, 0),
	YMM3 (RegType.SSE, Width.YWORD, 3, 0, 0),
	YMM4 (RegType.SSE, Width.YWORD, 4, 0, 0),
	YMM5 (RegType.SSE, Width.YWORD, 5, 0, 0),
	YMM6 (RegType.SSE, Width.YWORD, 6, 0, 0),
	YMM7 (RegType.SSE, Width.YWORD, 7, 0, 0),
	YMM8 (RegType.SSE, Width.YWORD, 0, 1, 0),
	YMM9 (RegType.SSE, Width.YWORD, 1, 1, 0),
	YMM10(RegType.SSE, Width.YWORD, 2, 1, 0),
	YMM11(RegType.SSE, Width.YWORD, 3, 1, 0),
	YMM12(RegType.SSE, Width.YWORD, 4, 1, 0),
	YMM13(RegType.SSE, Width.YWORD, 5, 1, 0),
	YMM14(RegType.SSE, Width.YWORD, 6, 1, 0),
	YMM15(RegType.SSE, Width.YWORD, 7, 1, 0),
	YMM16(RegType.SSE, Width.YWORD, 0, 0, 1),
	YMM17(RegType.SSE, Width.YWORD, 1, 0, 1),
	YMM18(RegType.SSE, Width.YWORD, 2, 0, 1),
	YMM19(RegType.SSE, Width.YWORD, 3, 0, 1),
	YMM20(RegType.SSE, Width.YWORD, 4, 0, 1),
	YMM21(RegType.SSE, Width.YWORD, 5, 0, 1),
	YMM22(RegType.SSE, Width.YWORD, 6, 0, 1),
	YMM23(RegType.SSE, Width.YWORD, 7, 0, 1),
	YMM24(RegType.SSE, Width.YWORD, 0, 1, 1),
	YMM25(RegType.SSE, Width.YWORD, 1, 1, 1),
	YMM26(RegType.SSE, Width.YWORD, 2, 1, 1),
	YMM27(RegType.SSE, Width.YWORD, 3, 1, 1),
	YMM28(RegType.SSE, Width.YWORD, 4, 1, 1),
	YMM29(RegType.SSE, Width.YWORD, 5, 1, 1),
	YMM30(RegType.SSE, Width.YWORD, 6, 1, 1),
	YMM31(RegType.SSE, Width.YWORD, 7, 1, 1),

	ZMM0 (RegType.SSE, Width.ZWORD, 0, 0, 0),
	ZMM1 (RegType.SSE, Width.ZWORD, 1, 0, 0),
	ZMM2 (RegType.SSE, Width.ZWORD, 2, 0, 0),
	ZMM3 (RegType.SSE, Width.ZWORD, 3, 0, 0),
	ZMM4 (RegType.SSE, Width.ZWORD, 4, 0, 0),
	ZMM5 (RegType.SSE, Width.ZWORD, 5, 0, 0),
	ZMM6 (RegType.SSE, Width.ZWORD, 6, 0, 0),
	ZMM7 (RegType.SSE, Width.ZWORD, 7, 0, 0),
	ZMM8 (RegType.SSE, Width.ZWORD, 0, 1, 0),
	ZMM9 (RegType.SSE, Width.ZWORD, 1, 1, 0),
	ZMM10(RegType.SSE, Width.ZWORD, 2, 1, 0),
	ZMM11(RegType.SSE, Width.ZWORD, 3, 1, 0),
	ZMM12(RegType.SSE, Width.ZWORD, 4, 1, 0),
	ZMM13(RegType.SSE, Width.ZWORD, 5, 1, 0),
	ZMM14(RegType.SSE, Width.ZWORD, 6, 1, 0),
	ZMM15(RegType.SSE, Width.ZWORD, 7, 1, 0),
	ZMM16(RegType.SSE, Width.ZWORD, 0, 0, 1),
	ZMM17(RegType.SSE, Width.ZWORD, 1, 0, 1),
	ZMM18(RegType.SSE, Width.ZWORD, 2, 0, 1),
	ZMM19(RegType.SSE, Width.ZWORD, 3, 0, 1),
	ZMM20(RegType.SSE, Width.ZWORD, 4, 0, 1),
	ZMM21(RegType.SSE, Width.ZWORD, 5, 0, 1),
	ZMM22(RegType.SSE, Width.ZWORD, 6, 0, 1),
	ZMM23(RegType.SSE, Width.ZWORD, 7, 0, 1),
	ZMM24(RegType.SSE, Width.ZWORD, 0, 1, 1),
	ZMM25(RegType.SSE, Width.ZWORD, 1, 1, 1),
	ZMM26(RegType.SSE, Width.ZWORD, 2, 1, 1),
	ZMM27(RegType.SSE, Width.ZWORD, 3, 1, 1),
	ZMM28(RegType.SSE, Width.ZWORD, 4, 1, 1),
	ZMM29(RegType.SSE, Width.ZWORD, 5, 1, 1),
	ZMM30(RegType.SSE, Width.ZWORD, 6, 1, 1),
	ZMM31(RegType.SSE, Width.ZWORD, 7, 1, 1),

	K0(RegType.MASK, Width.DWORD, 0, 0, 0),
	K1(RegType.MASK, Width.DWORD, 1, 0, 0),
	K2(RegType.MASK, Width.DWORD, 2, 0, 0),
	K3(RegType.MASK, Width.DWORD, 3, 0, 0),
	K4(RegType.MASK, Width.DWORD, 4, 0, 0),
	K5(RegType.MASK, Width.DWORD, 5, 0, 0),
	K6(RegType.MASK, Width.DWORD, 6, 0, 0),
	K7(RegType.MASK, Width.DWORD, 7, 0, 0);

	val string = name.lowercase()

	val isGP get() = type == RegType.GP
	val isSSE get() = type == RegType.SSE
	val isMMX get() = type == RegType.MMX
	val isFPU get() = type == RegType.FPU
	
}