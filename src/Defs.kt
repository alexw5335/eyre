package eyre

import java.nio.file.Path
import java.util.*



enum class Width(val bytes: Int) {

	BYTE(1),
	WORD(2),
	DWORD(4),
	QWORD(8),
	TWORD(10),
	XWORD(16),
	YWORD(32),
	ZWORD(64);

	val string = name.lowercase()
	val min: Long = if(bytes > 8) 0 else -(1L shl ((bytes shl 3) - 1))
	val max: Long = if(bytes > 8) 0 else (1L shl ((bytes shl 3) - 1)) - 1
	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

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



enum class Prefix(val avxValue: Int, val value: Int, val string: String?, val avxString: String) {
	NONE(0, 0, null, "NP"),
	P66(1, 0x66, "66", "66"),
	PF2(3, 0xF2, "F2", "F2"),
	PF3(2, 0xF3, "F3", "F3"),
	P9B(0, 0x9B, "9B", "9B");
}



enum class Escape(val avxValue: Int, val string: String?, val avxString: String) {
	NONE(0, null, "NE"),
	E0F(1, "0F", "0F"),
	E38(2, "0F 38", "38"),
	E3A(3, "0F 3A", "3A");
}



enum class OpEnc {
	RMV,
	RVM,
	MRV,
	MVR,
	VMR;
}



enum class VexW(val value: Int) {
	WIG(0),
	W0(0),
	W1(1);
}



enum class VexL(val value: Int) {
	LIG(0),
	L0(0),
	L1(1);
}



enum class UnOp(val string: String) {

	POS("+"),
	NEG("-",),
	NOT("~"),
	LNOT("!");

	fun calc(value: Int): Int = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0) 1 else 0
	}

	fun calc(value: Long): Long = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0L) 1L else 0L
	}

}



enum class BinOp(val precedence: Int, val string: String?) {

	ARR (10, null),
	DOT (10, "."),
	REF (9, "::"),
	MUL (8, "*"),
	DIV (8, "/"),
	ADD (7, "+"),
	SUB (7, "-"),
	SHL (6, "<<"),
	SHR (6, ">>"),
	SAR (6, ">>>"),
	GT  (5, ">"),
	LT  (5, "<"),
	GTE (5, ">="),
	LTE (5, "<="),
	EQ  (4, "=="),
	INEQ(4, "!="),
	AND (3, "&"),
	XOR (3, "^"),
	OR  (3, "|"),
	LAND(2, "&&"),
	LOR (2, "||"),
	SET (1, "=");

	fun calc(a: Int, b: Int): Int = when(this) {
		ARR -> 0
		DOT  -> 0
		REF  -> 0
		MUL  -> a * b
		DIV  -> a / b
		ADD  -> a + b
		SUB  -> a - b
		SHL  -> a shl b
		SHR  -> a shr b
		SAR  -> a ushr b
		GT   -> if(a > b) 1 else 0
		LT   -> if(a < b) 1 else 0
		GTE  -> if(a >= b) 1 else 0
		LTE  -> if(a <= b) 1 else 0
		EQ   -> if(a == b) 1 else 0
		INEQ -> if(a != b) 1 else 0
		AND  -> a and b
		XOR  -> a xor b
		OR   -> a or b
		LAND -> if(a != 0 && b != 0) 1 else 0
		LOR  -> if(a == 0 || b == 0) 1 else 0
		SET  -> 0
	}

	fun calc(a: Long, b: Long): Long = when(this) {
		ARR  -> 0
		DOT  -> 0
		REF  -> 0
		MUL  -> a * b
		DIV  -> a / b
		ADD  -> a + b
		SUB  -> a - b
		SHL  -> a shl b.toInt()
		SHR  -> a shr b.toInt()
		SAR  -> a ushr b.toInt()
		GT   -> if(a > b) 1 else 0
		LT   -> if(a < b) 1 else 0
		GTE  -> if(a >= b) 1 else 0
		LTE  -> if(a <= b) 1 else 0
		EQ   -> if(a == b) 1 else 0
		INEQ -> if(a != b) 1 else 0
		AND  -> a and b
		XOR  -> a xor b
		OR   -> a or b
		LAND -> if(a != 0L && b != 0L) 1 else 0
		LOR  -> if(a == 0L || b == 0L) 1 else 0
		SET  -> 0
	}

}



enum class MultiOps(vararg val parts: CompactOps, val width: Width? = null, val mr: Boolean = false) {
	RM(CompactOps.R, CompactOps.M),
	R_RM(CompactOps.R_R, CompactOps.R_M),
	RM_R(CompactOps.R_R, CompactOps.M_R, mr = true),
	O_A(CompactOps.A_O),
	MEM(CompactOps.M, width = null),
	M8(CompactOps.M, width = Width.BYTE),
	M16(CompactOps.M, width = Width.WORD),
	M32(CompactOps.M, width = Width.DWORD),
	M64(CompactOps.M, width = Width.QWORD),
	M80(CompactOps.M, width = Width.TWORD),
	M128(CompactOps.M, width = Width.XWORD);
}



enum class CompactOps(val mr: Boolean = false) {
	NONE,

	R,
	M,
	I8,
	I16,
	I32,
	AX,
	REL8,
	REL32,
	ST,
	FS,
	GS,
	O,

	R_R,
	R_M,
	M_R,
	RM_I,
	RM_I8,
	A_I,
	RM_1,
	RM_CL,
	A_O,
	ST0_ST,
	ST_ST0,

	// IMUL
	R_RM_I,
	R_RM_I8,
	// SHLD/SHRD
	RM_R_I8(mr = true),
	RM_R_CL(mr = true),

	// Non-functional ops (handled separately)

	// LEA/LFS/LGS/LSS
	R_MEM,
	// MOVSX/MOVZX/CRC32
	R_RM8,
	R_RM16,
	// MOVSXD
	R_RM32,
	// INVEPT/INVVPID/INVPCID
	R_M128,
	// ENQCMD/ENQCMDS/MOVDIR64B
	RA_M512,

	// ENTER
	I16_I8,
	// UMONITOR
	RA,

	// MOV
	O_I,
	R_SEG(mr = true),
	M_SEG(mr = true),
	SEG_R,
	SEG_M,
	A_MOF,
	MOF_A,
	R_DR(mr = true),
	DR_R,
	R_CR(mr = true),
	CR_R,

	// IN/OUT
	A_I8,
	I8_A,
	A_DX,
	DX_A,
}



enum class CompilerStage {
	LEX,
	PARSE,
	RESOLVE,
	ASSEMBLE,
	LINK;
}



class Reloc(
	val pos: Pos,
	val node: Node,
	val width: Width,
	val offset: Int,
	val rel: Boolean
)



class DllImports(val name: Name, val imports: HashMap<Name, DllImport>)



class DllDef(val name: Name, val exports: Set<Name>)



/**
 * Does not represent a section in a binary file. A section's index is not its place in
 * a resulting binary file.
 */
class Section(val index: Int, val name: String) {
	var pos = 0
	var addr = 0
	val present get() = addr != 0
}



@JvmInline
value class Place(val value: Long) {
	constructor(scope: Scope, name: Name) : this((scope.id.toLong() shl 32) or name.id.toLong())
	constructor() : this(0L)
	val scopeId get() = (value shr 32).toInt()
	val nameId get() = value.toInt()
	val scope get() = Scope[scopeId]
	val name get() = Name[nameId]
	override fun toString() = if(scope.isNull) name.toString() else "$scope.$name"
	val isNull get() = value == 0L
	val isNotNull get() = value != 0L
}



@JvmInline
value class Pos(val value: Long) {
	constructor(sec: Section, disp: Int) : this((sec.index.toLong() shl 32) or disp.toLong())
	constructor() : this(Long.MAX_VALUE)
	val secIndex get() = (value shr 32).toInt()
	val disp get() = value.toInt()
	override fun toString() = "$secIndex:$disp"
	val isNull get() = value == Long.MAX_VALUE
	val isNotNull get() = value != Long.MAX_VALUE
}



@JvmInline
value class SrcPos(val value: Int) {
	constructor(file: SrcFile, line: Int) : this((line and 0xFFFF) or ((file.index) shl 16))
	constructor() : this(Int.MAX_VALUE)
	val line get() = value and 0xFFFF
	val file get() = value shr 16
	override fun toString() = "$file:$line"
	val isNull get() = value == Int.MAX_VALUE
	val isNotNull get() = value != Int.MAX_VALUE
}



class SrcFile(val index: Int, val path: Path, val relPath: Path) {
	val tokens    = ArrayList<Token>()
	val nodes     = ArrayList<TopNode>()
	var invalid   = false
	var lineCount = 0
	var resolved  = false
	var resolving = false
}



class EyreError(var srcPos: SrcPos, message: String) : Exception(message)