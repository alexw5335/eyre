package eyre



enum class Width(val varString: String, val bytes: Int) {

	BYTE("db", 1),
	WORD("dw", 2),
	DWORD("dd", 4),
	QWORD("dq", 8);

	val string = name.lowercase()
	val bit = 1 shl ordinal
	val min = -(1 shl ((bytes shl 3) - 1))
	val max = (1 shl ((bytes shl 3) - 1)) - 1
	val immLength = bytes.coerceAtMost(4)

	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

}



@JvmInline
value class Widths(val value: Int) {
	operator fun contains(width: Width) = value and width.bit != 0
}



enum class SegReg {
	FS,
	GS;
}



enum class Register(val value: Int, val width: Width, val flags: Int) {

	RAX(0, Width.QWORD, 2),
	RCX(1, Width.QWORD, 0),
	RDX(2, Width.QWORD, 0),
	RBX(3, Width.QWORD, 0),
	RSP(4, Width.QWORD, 4),
	RBP(5, Width.QWORD, 0),
	RSI(6, Width.QWORD, 0),
	RDI(7, Width.QWORD, 0),
	R8 (0, Width.QWORD, 1),
	R9 (1, Width.QWORD, 1),
	R10(2, Width.QWORD, 1),
	R11(3, Width.QWORD, 1),
	R12(4, Width.QWORD, 1),
	R13(5, Width.QWORD, 1),
	R14(6, Width.QWORD, 1),
	R15(7, Width.QWORD, 1),

	EAX (0, Width.DWORD, 2),
	ECX (1, Width.DWORD, 0),
	EDX (2, Width.DWORD, 0),
	EBX (3, Width.DWORD, 0),
	ESP (4, Width.DWORD, 4),
	EBP (5, Width.DWORD, 0),
	ESI (6, Width.DWORD, 0),
	EDI (7, Width.DWORD, 0),
	R8D (0, Width.DWORD, 1),
	R9D (1, Width.DWORD, 1),
	R10D(2, Width.DWORD, 1),
	R11D(3, Width.DWORD, 1),
	R12D(4, Width.DWORD, 1),
	R13D(5, Width.DWORD, 1),
	R14D(6, Width.DWORD, 1),
	R15D(7, Width.DWORD, 1),

	AX  (0, Width.WORD, 2),
	CX  (1, Width.WORD, 0),
	DX  (2, Width.WORD, 0),
	BX  (3, Width.WORD, 0),
	SP  (4, Width.WORD, 4),
	BP  (5, Width.WORD, 0),
	SI  (6, Width.WORD, 0),
	DI  (7, Width.WORD, 0),
	R8W (0, Width.WORD, 1),
	R9W (1, Width.WORD, 1),
	R10W(2, Width.WORD, 1),
	R11W(3, Width.WORD, 1),
	R12W(4, Width.WORD, 1),
	R13W(5, Width.WORD, 1),
	R14W(6, Width.WORD, 1),
	R15W(7, Width.WORD, 1),

	AL  (0, Width.BYTE, 2),
	CL  (1, Width.BYTE, 0),
	DL  (2, Width.BYTE, 0),
	BL  (3, Width.BYTE, 0),
	AH  (4, Width.BYTE, 8),
	CH  (5, Width.BYTE, 8),
	DH  (6, Width.BYTE, 8),
	BH  (7, Width.BYTE, 8),
	R8B (0, Width.BYTE, 1),
	R9B (1, Width.BYTE, 1),
	R10B(2, Width.BYTE, 1),
	R11B(3, Width.BYTE, 1),
	R12B(4, Width.BYTE, 1),
	R13B(5, Width.BYTE, 1),
	R14B(6, Width.BYTE, 1),
	R15B(7, Width.BYTE, 1),

	SPL(4, Width.BYTE, 20),
	BPL(5, Width.BYTE, 16),
	SIL(6, Width.BYTE, 16),
	DIL(7, Width.BYTE, 16);

	val string = name.lowercase()

	val rex get() = flags and 1
	val isA get() = flags and 2 != 0
	val isSP get() = flags and 4 != 0
	val rex8 get() = flags and 8 != 0
	val noRex8 get() = flags and 16 != 0
	val invalidBase get() = value == 5
	val isSpOr12 get() = value == 4

}



enum class Section {

	/** Invalid */
	NONE,

	/** .text, initialised | code, execute | read */
	TEXT,

	/** .data, initialised, read | write*/
	DATA,

	/** .idata, initialised, read */
	IDATA;

}



enum class UnaryOp(
	val symbol     : String,
	val calculate  : (Long) -> Long,
) {

	POS("+", { it }),
	NEG("-", { -it }),
	NOT("~", { it.inv() }),
	LNOT("!", { if(it == 0L) 1L else 0L });

}




enum class BinaryOp(
	val symbol          : String?,
	val precedence      : Int,
	val calculate       : (Long, Long) -> Long
) {

	DOT (null,  9, { _, _ -> 0L }),

	REF (null,  8, { _,_ -> 0L }),

	MUL ("*",   7, { a, b -> a * b }),
	DIV ("/",   7, { a, b -> a / b }),

	ADD ("+",   6, { a, b -> a + b }),
	SUB ("-",   6, { a, b -> a - b }),

	SHL ("<<",  5, { a, b -> a shl b.toInt() }),
	SHR (">>",  5, { a, b -> a shr b.toInt() }),
	SAR (">>>", 5, { a, b -> a ushr b.toInt() }),

	GT  (">",   4, { a, b -> if(a > b) 1 else 0 }),
	LT  ("<",   4, { a, b -> if(a < b) 1 else 0 }),
	GTE (">=",  4, { a, b -> if(a >= b) 1 else 0 }),
	LTE ("<=",  4, { a, b -> if(a <= b) 1 else 0 }),

	EQ  ("==",  3, { a, b -> if(a == b) 1 else 0 }),
	INEQ("!=",  3, { a, b -> if(a != b) 1 else 0 }),

	AND ("&",   2, { a, b -> a and b }),
	XOR ("^",   2, { a, b -> a xor b }),
	OR  ("|",   2, { a, b -> a or b }),

	LAND("&&",  1, { a, b -> if(a != 0L && b != 0L) 1 else 0 }),
	LOR ("||",  1, { a, b -> if(a != 0L || b != 0L) 1 else 0 }),


}



enum class Prefix(val value: Int) {

	REP(0xF3),
	REPE(0xF3),
	REPZ(0xF3),
	REPNE(0xF2),
	REPNZ(0xF2),
	LOCK(0xF0);

	val string = name.lowercase()

}



enum class Keyword {

	CONST,
	VAR,
	IMPORT,
	ENUM,
	NAMESPACE,
	FLAGS,
	STRUCT,
	PROC,
	BITMASK,
	DLLIMPORT;

	val string = name.lowercase()

}



enum class Operands {

	NONE,
	R,
	M,
	R_R,
	R_M,
	M_R,
	R_I,
	M_I,
	CUSTOM1,
	CUSTOM2,
	CUSTOM3,

}



enum class CustomOperands {

	O,
	I,
	I8,
	A_I,
	RM_1,
	RM_CL,
	RM_I8,
	REL8,
	REL32,
	R64_RM32,
	R_RM8,
	R_RM16,
	I16,
	A_D,
	I_A,
	D_A,
	R_RM_I8,
	R_RM_I,
	A_O,
	O_I,
	FS,
	GS,

}



enum class CompoundOperands(vararg val operandsList: Operands) {
	RM(Operands.R, Operands.M),
	RM_R(Operands.R_R, Operands.M_R),
	R_RM(Operands.R_R, Operands.R_M),
	RM_I(Operands.R_I, Operands.M_I),
}



data class Encoding(
	val opcode    : Int,
	val extension : Int,
	val prefix    : Int,
	val widths    : Widths
)



class EncodingGroup(
	val operandsBits : Int,
	val encodings    : List<Encoding>,
)



class SectionData(val size: Int, val rva: Int, val pos: Int)