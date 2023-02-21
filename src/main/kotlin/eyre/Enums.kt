package eyre



enum class Width(val string: String, val varString: String, val bytes: Int) {

	BIT8("byte", "db", 1),
	BIT16("word", "dw", 2),
	BIT32("dword", "dd", 4),
	BIT64("qword", "dq", 8);

	val bit = 1 shl ordinal
	val min = -(1 shl ((bytes shl 3) - 1))
	val max = (1 shl ((bytes shl 3) - 1)) - 1

	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

}



enum class Widths(val bits: Int) {

	NONE  (0b0000),
	ALL   (0b1111),
	NO8   (0b1110),
	NO64  (0b0111),
	NO816 (0b1100),
	NO832 (0b1010),
	NO864 (0b0110),
	ONLY8 (0b0001),
	ONLY16(0b0010),
	ONLY64(0b1000);

	operator fun contains(width: Width) = bits and width.bit != 0

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



enum class Mnemonic {

	ADD, OR, ADC, SBB, AND, SUB, XOR, CMP, ROL, ROR, RCL, RCR, SHL, SAL, SHR, SAR,
	CBW, CWDE, CDQE, CWD, CDQ, CQO, CLC, CLD, CLI, CLTS, CMC, STC, STI, STD, BSWAP,
	CMPSB, CMPSW, CMPSD, CMPSQ, SCASB, SCASW, SCASD, SCASQ, STOSB, STOSW, STOSD, STOSQ,
	LODSB, LODSW, LODSD, LODSQ, MOVSB, MOVSW, MOVSD, MOVSQ, INSB, INSW, INSD,
	OUTSB, OUTSW, OUTSD, CPUID, RET, RETF, SHLD, SHRD, MOVZX, MOVSX, MOVSXD,
	CMOVA, CMOVAE, CMOVB, CMOVBE, CMOVC, CMOVE, CMOVG, CMOVGE, CMOVL, CMOVLE, CMOVNA,
	CMOVNAE, CMOVNB, CMOVNBE, CMOVNC, CMOVNE, CMOVNG, CMOVNGE, CMOVNL, CMOVNLE, CMOVNO,
	CMOVNP, CMOVNS, CMOVNZ, CMOVO, CMOVP, CMOVPE, CMOVPO, CMOVS, CMOVZ,
	SETA, SETAE, SETB, SETBE, SETC, SETE, SETG, SETGE, SETL, SETLE, SETNA, SETNAE,
	SETNB, SETNBE, SETNC, SETNE, SETNG, SETNGE, SETNL, SETNLE, SETNO, SETNP, SETNS,
	SETNZ, SETO, SETP, SETPE, SETPO, SETS, SETZ,
	JA, JAE, JB, JBE, JC, JE, JG, JGE, JL, JLE, JNA, JNAE, JNB, JNBE, JNC, JNE,
	JNG, JNGE, JNL, JNLE, JNO, JNP, JNS, JNZ, JO, JP, JPE, JPO, JS, JZ,
	JCXZ, JECXZ, JRCXZ, LOOP, LOOPE, LOOPNE, CALL, CALLF, JMP, JMPF,
	PUSH, POP, POPW_FS, PUSHW_FS, PUSHW_GS, PUSH_GS, POP_FS, PUSH_FS, POP_GS, POPW_GS,
	INT1, INT3, INT, HLT, INC, DEC, PUSHF, PUSHFQ, LAHF, IN, OUT, WAIT, FWAIT,
	NOP, LEA, XCHG, MOV, IRETW, IRETD, IRETQ, TEST, NOT, NEG, MUL, IMUL, DIV, IDIV, MFENCE,
	LEAVE, LEAVEW, ENTER, ENTERW, BT, BTC, BTR, BTS, BSF, BSR, RSM, MOVNTI,
	SLDT, LLDT, STR, LTR, SGDT, SIDT, VERR, VERW, VMCALL, VMLAUNCH, VMRESUME, VMXOFF,
	FADD, FADDP, FIADD, FMUL, FMULP, FIMUL, FCOM, FCOMP, FCOMPP, FSUB, FSUBP, FISUB,
	FSUBR, FSUBRP, FISUBR, FDIV, FDIVP, FIDIV, FDIVR, FDIVRP, FIDVR, FLD, FXCH, FST,
	FSTP, FNOP, FLDENV, FCHS, FABS, FTST, FXAM, FLDCW, FLD1, FLDL2T, FLDL2E, FLDPI,
	FLDLG2, FLDLN2, FLDZ, FSTENV, FNSTENV, F2XM1, FYL2X, FPTAN, FPATAN, FXTRACT, FPREM1,
	FDECSTP, FINCSTP, FPREM, FYL2XP1, FSQRT, FSINCOS, FRNDINT, FSCALE, FSIN, FCOS, FNSTCW,
	FSTCW, FCMOVB, FCMOVE, FCMOVBE, FCMOVU, FCMOVNB, FCMOVNE, FCMOVNBE, FCMOVNU, FUCOM,
	FUCOMP, FUCOMPP, FILD, FISTTP, FCLEX, FNCLEX, FINIT, FNINIT, FCOMI, FCOMIP, FUCOMI,
	FUCOMIP, FFREE, FRSTOR, FICOM, FICOMP, FSAVE, FNSAVE, FSTSW, FNSTSW, FIST, FISTP, FBSTP,
	RDRAND, RDSEED;

	val string = name.lowercase()

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