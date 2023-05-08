package eyre



enum class RegType {
	GP8, 
	GP16,
	GP32, 
	GP64, 
	XMM, 
	YMM, 
	ZMM, 
	MMX, 
	FPU,
	MASK,
	SEG;
	val bit = 1 shl ordinal
}



@JvmInline
value class RegMask(val value: Int) {
	operator fun contains(type: RegType) = type.bit and value != 0
	operator fun contains(reg: Reg) = reg.type.bit and value != 0
	operator fun contains(width: Width) = width.bit and value != 0
	companion object {
		val GP8      = RegMask(0b0001)
		val GP16     = RegMask(0b0010)
		val GP32     = RegMask(0b0100)
		val GP64     = RegMask(0b1000)
		val GP       = RegMask(0b1111)
		val GP816    = RegMask(0b0011)
		val GP81632  = RegMask(0b0111)
		val GP1664   = RegMask(0b1010)
		val GP163264 = RegMask(0b1110)
		val FPU      = RegMask(0b0001_0000)
		val SSE      = RegMask(0b1110_0000)
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

	RAX(RegType.GP64, Width.QWORD, 0, 0, 0),
	RCX(RegType.GP64, Width.QWORD, 1, 0, 0),
	RDX(RegType.GP64, Width.QWORD, 2, 0, 0),
	RBX(RegType.GP64, Width.QWORD, 3, 0, 0),
	RSP(RegType.GP64, Width.QWORD, 4, 0, 0),
	RBP(RegType.GP64, Width.QWORD, 5, 0, 0),
	RSI(RegType.GP64, Width.QWORD, 6, 0, 0),
	RDI(RegType.GP64, Width.QWORD, 7, 0, 0),
	R8 (RegType.GP64, Width.QWORD, 0, 1, 0),
	R9 (RegType.GP64, Width.QWORD, 1, 1, 0),
	R10(RegType.GP64, Width.QWORD, 2, 1, 0),
	R11(RegType.GP64, Width.QWORD, 3, 1, 0),
	R12(RegType.GP64, Width.QWORD, 4, 1, 0),
	R13(RegType.GP64, Width.QWORD, 5, 1, 0),
	R14(RegType.GP64, Width.QWORD, 6, 1, 0),
	R15(RegType.GP64, Width.QWORD, 7, 1, 0),

	EAX (RegType.GP32, Width.DWORD, 0, 0, 0),
	ECX (RegType.GP32, Width.DWORD, 1, 0, 0),
	EDX (RegType.GP32, Width.DWORD, 2, 0, 0),
	EBX (RegType.GP32, Width.DWORD, 3, 0, 0),
	ESP (RegType.GP32, Width.DWORD, 4, 0, 0),
	EBP (RegType.GP32, Width.DWORD, 5, 0, 0),
	ESI (RegType.GP32, Width.DWORD, 6, 0, 0),
	EDI (RegType.GP32, Width.DWORD, 7, 0, 0),
	R8D (RegType.GP32, Width.DWORD, 0, 1, 0),
	R9D (RegType.GP32, Width.DWORD, 1, 1, 0),
	R10D(RegType.GP32, Width.DWORD, 2, 1, 0),
	R11D(RegType.GP32, Width.DWORD, 3, 1, 0),
	R12D(RegType.GP32, Width.DWORD, 4, 1, 0),
	R13D(RegType.GP32, Width.DWORD, 5, 1, 0),
	R14D(RegType.GP32, Width.DWORD, 6, 1, 0),
	R15D(RegType.GP32, Width.DWORD, 7, 1, 0),

	AX  (RegType.GP16, Width.WORD, 0, 0, 0),
	CX  (RegType.GP16, Width.WORD, 1, 0, 0),
	DX  (RegType.GP16, Width.WORD, 2, 0, 0),
	BX  (RegType.GP16, Width.WORD, 3, 0, 0),
	SP  (RegType.GP16, Width.WORD, 4, 0, 0),
	BP  (RegType.GP16, Width.WORD, 5, 0, 0),
	SI  (RegType.GP16, Width.WORD, 6, 0, 0),
	DI  (RegType.GP16, Width.WORD, 7, 0, 0),
	R8W (RegType.GP16, Width.WORD, 0, 1, 0),
	R9W (RegType.GP16, Width.WORD, 1, 1, 0),
	R10W(RegType.GP16, Width.WORD, 2, 1, 0),
	R11W(RegType.GP16, Width.WORD, 3, 1, 0),
	R12W(RegType.GP16, Width.WORD, 4, 1, 0),
	R13W(RegType.GP16, Width.WORD, 5, 1, 0),
	R14W(RegType.GP16, Width.WORD, 6, 1, 0),
	R15W(RegType.GP16, Width.WORD, 7, 1, 0),

	AL  (RegType.GP8, Width.BYTE, 0, 0, 0),
	CL  (RegType.GP8, Width.BYTE, 1, 0, 0),
	DL  (RegType.GP8, Width.BYTE, 2, 0, 0),
	BL  (RegType.GP8, Width.BYTE, 3, 0, 0),
	AH  (RegType.GP8, Width.BYTE, 4, 0, 0, isRex8 = true),
	BH  (RegType.GP8, Width.BYTE, 5, 0, 0, isRex8 = true),
	CH  (RegType.GP8, Width.BYTE, 6, 0, 0, isRex8 = true),
	DH  (RegType.GP8, Width.BYTE, 7, 0, 0, isRex8 = true),
	R8B (RegType.GP8, Width.BYTE, 0, 1, 0),
	R9B (RegType.GP8, Width.BYTE, 1, 1, 0),
	R10B(RegType.GP8, Width.BYTE, 2, 1, 0),
	R11B(RegType.GP8, Width.BYTE, 3, 1, 0),
	R12B(RegType.GP8, Width.BYTE, 4, 1, 0),
	R13B(RegType.GP8, Width.BYTE, 5, 1, 0),
	R14B(RegType.GP8, Width.BYTE, 6, 1, 0),
	R15B(RegType.GP8, Width.BYTE, 7, 1, 0),
	
	SPL(RegType.GP8, Width.BYTE, 0, 1, 0, noRex = true),
	BPL(RegType.GP8, Width.BYTE, 1, 1, 0, noRex = true),
	SIL(RegType.GP8, Width.BYTE, 2, 1, 0, noRex = true),
	DIL(RegType.GP8, Width.BYTE, 3, 1, 0, noRex = true),

	FS(RegType.SEG, Width.WORD, 0, 0, 0),
	GS(RegType.SEG, Width.WORD, 0, 0, 0),

	ST0(RegType.FPU, Width.TWORD, 0, 0, 0),
	ST1(RegType.FPU, Width.TWORD, 1, 0, 0),
	ST2(RegType.FPU, Width.TWORD, 2, 0, 0),
	ST3(RegType.FPU, Width.TWORD, 3, 0, 0),
	ST4(RegType.FPU, Width.TWORD, 4, 0, 0),
	ST5(RegType.FPU, Width.TWORD, 5, 0, 0),
	ST6(RegType.FPU, Width.TWORD, 6, 0, 0),
	ST7(RegType.FPU, Width.TWORD, 7, 0, 0),

	MM0(RegType.MMX, Width.QWORD, 0, 0, 0),
	MM1(RegType.MMX, Width.QWORD, 1, 0, 0),
	MM2(RegType.MMX, Width.QWORD, 2, 0, 0),
	MM3(RegType.MMX, Width.QWORD, 3, 0, 0),
	MM4(RegType.MMX, Width.QWORD, 4, 0, 0),
	MM5(RegType.MMX, Width.QWORD, 5, 0, 0),
	MM6(RegType.MMX, Width.QWORD, 6, 0, 0),
	MM7(RegType.MMX, Width.QWORD, 7, 0, 0),
	
	XMM0 (RegType.XMM, Width.XWORD, 0, 0, 0),
	XMM1 (RegType.XMM, Width.XWORD, 1, 0, 0),
	XMM2 (RegType.XMM, Width.XWORD, 2, 0, 0),
	XMM3 (RegType.XMM, Width.XWORD, 3, 0, 0),
	XMM4 (RegType.XMM, Width.XWORD, 4, 0, 0),
	XMM5 (RegType.XMM, Width.XWORD, 5, 0, 0),
	XMM6 (RegType.XMM, Width.XWORD, 6, 0, 0),
	XMM7 (RegType.XMM, Width.XWORD, 7, 0, 0),
	XMM8 (RegType.XMM, Width.XWORD, 0, 1, 0),
	XMM9 (RegType.XMM, Width.XWORD, 1, 1, 0),
	XMM10(RegType.XMM, Width.XWORD, 2, 1, 0),
	XMM11(RegType.XMM, Width.XWORD, 3, 1, 0),
	XMM12(RegType.XMM, Width.XWORD, 4, 1, 0),
	XMM13(RegType.XMM, Width.XWORD, 5, 1, 0),
	XMM14(RegType.XMM, Width.XWORD, 6, 1, 0),
	XMM15(RegType.XMM, Width.XWORD, 7, 1, 0),
	XMM16(RegType.XMM, Width.XWORD, 0, 0, 1),
	XMM17(RegType.XMM, Width.XWORD, 1, 0, 1),
	XMM18(RegType.XMM, Width.XWORD, 2, 0, 1),
	XMM19(RegType.XMM, Width.XWORD, 3, 0, 1),
	XMM20(RegType.XMM, Width.XWORD, 4, 0, 1),
	XMM21(RegType.XMM, Width.XWORD, 5, 0, 1),
	XMM22(RegType.XMM, Width.XWORD, 6, 0, 1),
	XMM23(RegType.XMM, Width.XWORD, 7, 0, 1),
	XMM24(RegType.XMM, Width.XWORD, 0, 1, 1),
	XMM25(RegType.XMM, Width.XWORD, 1, 1, 1),
	XMM26(RegType.XMM, Width.XWORD, 2, 1, 1),
	XMM27(RegType.XMM, Width.XWORD, 3, 1, 1),
	XMM28(RegType.XMM, Width.XWORD, 4, 1, 1),
	XMM29(RegType.XMM, Width.XWORD, 5, 1, 1),
	XMM30(RegType.XMM, Width.XWORD, 6, 1, 1),
	XMM31(RegType.XMM, Width.XWORD, 7, 1, 1),

	YMM0 (RegType.YMM, Width.YWORD, 0, 0, 0),
	YMM1 (RegType.YMM, Width.YWORD, 1, 0, 0),
	YMM2 (RegType.YMM, Width.YWORD, 2, 0, 0),
	YMM3 (RegType.YMM, Width.YWORD, 3, 0, 0),
	YMM4 (RegType.YMM, Width.YWORD, 4, 0, 0),
	YMM5 (RegType.YMM, Width.YWORD, 5, 0, 0),
	YMM6 (RegType.YMM, Width.YWORD, 6, 0, 0),
	YMM7 (RegType.YMM, Width.YWORD, 7, 0, 0),
	YMM8 (RegType.YMM, Width.YWORD, 0, 1, 0),
	YMM9 (RegType.YMM, Width.YWORD, 1, 1, 0),
	YMM10(RegType.YMM, Width.YWORD, 2, 1, 0),
	YMM11(RegType.YMM, Width.YWORD, 3, 1, 0),
	YMM12(RegType.YMM, Width.YWORD, 4, 1, 0),
	YMM13(RegType.YMM, Width.YWORD, 5, 1, 0),
	YMM14(RegType.YMM, Width.YWORD, 6, 1, 0),
	YMM15(RegType.YMM, Width.YWORD, 7, 1, 0),
	YMM16(RegType.YMM, Width.YWORD, 0, 0, 1),
	YMM17(RegType.YMM, Width.YWORD, 1, 0, 1),
	YMM18(RegType.YMM, Width.YWORD, 2, 0, 1),
	YMM19(RegType.YMM, Width.YWORD, 3, 0, 1),
	YMM20(RegType.YMM, Width.YWORD, 4, 0, 1),
	YMM21(RegType.YMM, Width.YWORD, 5, 0, 1),
	YMM22(RegType.YMM, Width.YWORD, 6, 0, 1),
	YMM23(RegType.YMM, Width.YWORD, 7, 0, 1),
	YMM24(RegType.YMM, Width.YWORD, 0, 1, 1),
	YMM25(RegType.YMM, Width.YWORD, 1, 1, 1),
	YMM26(RegType.YMM, Width.YWORD, 2, 1, 1),
	YMM27(RegType.YMM, Width.YWORD, 3, 1, 1),
	YMM28(RegType.YMM, Width.YWORD, 4, 1, 1),
	YMM29(RegType.YMM, Width.YWORD, 5, 1, 1),
	YMM30(RegType.YMM, Width.YWORD, 6, 1, 1),
	YMM31(RegType.YMM, Width.YWORD, 7, 1, 1),

	ZMM0 (RegType.ZMM, Width.ZWORD, 0, 0, 0),
	ZMM1 (RegType.ZMM, Width.ZWORD, 1, 0, 0),
	ZMM2 (RegType.ZMM, Width.ZWORD, 2, 0, 0),
	ZMM3 (RegType.ZMM, Width.ZWORD, 3, 0, 0),
	ZMM4 (RegType.ZMM, Width.ZWORD, 4, 0, 0),
	ZMM5 (RegType.ZMM, Width.ZWORD, 5, 0, 0),
	ZMM6 (RegType.ZMM, Width.ZWORD, 6, 0, 0),
	ZMM7 (RegType.ZMM, Width.ZWORD, 7, 0, 0),
	ZMM8 (RegType.ZMM, Width.ZWORD, 0, 1, 0),
	ZMM9 (RegType.ZMM, Width.ZWORD, 1, 1, 0),
	ZMM10(RegType.ZMM, Width.ZWORD, 2, 1, 0),
	ZMM11(RegType.ZMM, Width.ZWORD, 3, 1, 0),
	ZMM12(RegType.ZMM, Width.ZWORD, 4, 1, 0),
	ZMM13(RegType.ZMM, Width.ZWORD, 5, 1, 0),
	ZMM14(RegType.ZMM, Width.ZWORD, 6, 1, 0),
	ZMM15(RegType.ZMM, Width.ZWORD, 7, 1, 0),
	ZMM16(RegType.ZMM, Width.ZWORD, 0, 0, 1),
	ZMM17(RegType.ZMM, Width.ZWORD, 1, 0, 1),
	ZMM18(RegType.ZMM, Width.ZWORD, 2, 0, 1),
	ZMM19(RegType.ZMM, Width.ZWORD, 3, 0, 1),
	ZMM20(RegType.ZMM, Width.ZWORD, 4, 0, 1),
	ZMM21(RegType.ZMM, Width.ZWORD, 5, 0, 1),
	ZMM22(RegType.ZMM, Width.ZWORD, 6, 0, 1),
	ZMM23(RegType.ZMM, Width.ZWORD, 7, 0, 1),
	ZMM24(RegType.ZMM, Width.ZWORD, 0, 1, 1),
	ZMM25(RegType.ZMM, Width.ZWORD, 1, 1, 1),
	ZMM26(RegType.ZMM, Width.ZWORD, 2, 1, 1),
	ZMM27(RegType.ZMM, Width.ZWORD, 3, 1, 1),
	ZMM28(RegType.ZMM, Width.ZWORD, 4, 1, 1),
	ZMM29(RegType.ZMM, Width.ZWORD, 5, 1, 1),
	ZMM30(RegType.ZMM, Width.ZWORD, 6, 1, 1),
	ZMM31(RegType.ZMM, Width.ZWORD, 7, 1, 1),

	K0(RegType.MASK, Width.DWORD, 0, 0, 0),
	K1(RegType.MASK, Width.DWORD, 1, 0, 0),
	K2(RegType.MASK, Width.DWORD, 2, 0, 0),
	K3(RegType.MASK, Width.DWORD, 3, 0, 0),
	K4(RegType.MASK, Width.DWORD, 4, 0, 0),
	K5(RegType.MASK, Width.DWORD, 5, 0, 0),
	K6(RegType.MASK, Width.DWORD, 6, 0, 0),
	K7(RegType.MASK, Width.DWORD, 7, 0, 0);

	val string = name.lowercase()
	
}