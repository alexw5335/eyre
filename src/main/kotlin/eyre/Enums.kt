package eyre



enum class Width(val string: String, val varString: String, val bytes: Int) {

	BIT8("byte", "db", 1),
	BIT16("word", "dw", 2),
	BIT32("dword", "dd", 4),
	BIT64("qword", "dq", 8);

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



enum class Register(val value: Int, val width: Width, val flags: Int) {

	RAX(0, Width.BIT64, 2),
	RCX(1, Width.BIT64, 0),
	RDX(2, Width.BIT64, 0),
	RBX(3, Width.BIT64, 0),
	RSP(4, Width.BIT64, 4),
	RBP(5, Width.BIT64, 0),
	RSI(6, Width.BIT64, 0),
	RDI(7, Width.BIT64, 0),
	R8 (0, Width.BIT64, 1),
	R9 (1, Width.BIT64, 1),
	R10(2, Width.BIT64, 1),
	R11(3, Width.BIT64, 1),
	R12(4, Width.BIT64, 1),
	R13(5, Width.BIT64, 1),
	R14(6, Width.BIT64, 1),
	R15(7, Width.BIT64, 1),

	EAX (0, Width.BIT32, 2),
	ECX (1, Width.BIT32, 0),
	EDX (2, Width.BIT32, 0),
	EBX (3, Width.BIT32, 0),
	ESP (4, Width.BIT32, 4),
	EBP (5, Width.BIT32, 0),
	ESI (6, Width.BIT32, 0),
	EDI (7, Width.BIT32, 0),
	R8D (0, Width.BIT32, 1),
	R9D (1, Width.BIT32, 1),
	R10D(2, Width.BIT32, 1),
	R11D(3, Width.BIT32, 1),
	R12D(4, Width.BIT32, 1),
	R13D(5, Width.BIT32, 1),
	R14D(6, Width.BIT32, 1),
	R15D(7, Width.BIT32, 1),

	AX  (0, Width.BIT16, 2),
	CX  (1, Width.BIT16, 0),
	DX  (2, Width.BIT16, 0),
	BX  (3, Width.BIT16, 0),
	SP  (4, Width.BIT16, 4),
	BP  (5, Width.BIT16, 0),
	SI  (6, Width.BIT16, 0),
	DI  (7, Width.BIT16, 0),
	R8W (0, Width.BIT16, 1),
	R9W (1, Width.BIT16, 1),
	R10W(2, Width.BIT16, 1),
	R11W(3, Width.BIT16, 1),
	R12W(4, Width.BIT16, 1),
	R13W(5, Width.BIT16, 1),
	R14W(6, Width.BIT16, 1),
	R15W(7, Width.BIT16, 1),

	AL  (0, Width.BIT8, 2),
	CL  (1, Width.BIT8, 0),
	DL  (2, Width.BIT8, 0),
	BL  (3, Width.BIT8, 0),
	AH  (4, Width.BIT8, 8),
	CH  (5, Width.BIT8, 8),
	DH  (6, Width.BIT8, 8),
	BH  (7, Width.BIT8, 8),
	R8B (0, Width.BIT8, 1),
	R9B (1, Width.BIT8, 1),
	R10B(2, Width.BIT8, 1),
	R11B(3, Width.BIT8, 1),
	R12B(4, Width.BIT8, 1),
	R13B(5, Width.BIT8, 1),
	R14B(6, Width.BIT8, 1),
	R15B(7, Width.BIT8, 1),

	SPL(4, Width.BIT8, 20),
	BPL(5, Width.BIT8, 16),
	SIL(6, Width.BIT8, 16),
	DIL(7, Width.BIT8, 16);

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
	NOT("~", { it.inv() });

}



enum class BinaryOp(
	val symbol          : String?,
	val precedence      : Int,
	val calculate       : (Long, Long) -> Long
) {

	DOT(null, 5, { _, _ -> 0L }),
	MUL("*",  4, { a, b -> a * b }),
	DIV("/",  4, { a, b -> a / b }),
	ADD("+",  3, { a, b -> a + b }),
	SUB("-",  3, { a, b -> a - b }),
	SHL("<<", 2, { a, b -> a shl b.toInt() }),
	SHR(">>", 2, { a, b -> a shr b.toInt() }),
	AND("&",  1, { a, b -> a and b }),
	XOR("^",  1, { a, b -> a xor b }),
	OR( "|",  1, { a, b -> a or b });


	val isLeftRegValid get() = this == ADD || this == SUB
	val isRightRegValid get() = this == ADD

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
	O_I

}



enum class CompoundOperands(vararg val operandsList: Operands) {
	RM(Operands.R, Operands.M),
	RM_R(Operands.R_R, Operands.M_R),
	R_RM(Operands.R_R, Operands.R_M),
	RM_I(Operands.R_I, Operands.M_I),
}



class Encoding(
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