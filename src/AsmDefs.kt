package eyre



enum class Mnemonic {
	NONE,
	ADD,
	OR,
	ADC,
	SBB,
	AND,
	SUB,
	XOR,
	CMP,
	PUSH,
	POP,
	MOVSXD,
	MOVSX,
	MOVZX,
	IMUL,
	JO,
	JNO,
	JB,
	JNAE,
	JC,
	JNB,
	JAE,
	JNC,
	JZ,
	JE,
	JNZ,
	JNE,
	JBE,
	JNA,
	JNBE,
	JA,
	JS,
	JNS,
	JP,
	JPE,
	JNP,
	JPO,
	JL,
	JNGE,
	JNL,
	JGE,
	JLE,
	JNG,
	JNLE,
	JG,
	TEST,
	XCHG,
	MOV,
	LEA,
	NOP,
	WAIT,
	INT1,
	SAHF,
	LAHF,
	CBW,
	CWDE,
	CDQE,
	CWD,
	CDQ,
	CQO,
	INSB,
	INSW,
	INSD,
	OUTSB,
	OUTSW,
	OUTSD,
	MOVSB,
	MOVSW,
	MOVSD,
	MOVSQ,
	CMPSB,
	CMPSW,
	CMPSD,
	CMPSQ,
	STOSB,
	STOSW,
	STOSD,
	STOSQ,
	LODSB,
	LODSW,
	LODSD,
	LODSQ,
	SCASB,
	SCASW,
	SCASD,
	SCASQ,
	ROL,
	ROR,
	RCL,
	RCR,
	SAL,
	SHL,
	SHR,
	SAR,
	RET,
	LEAVE,
	INT3,
	INT,
	IRET,
	IN,
	OUT,
	CALL,
	CALLF,
	JMP,
	JMPF,
	HLT,
	CMC,
	NOT,
	NEG,
	MUL,
	DIV,
	IDIV,
	CLC,
	STC,
	CLI,
	STI,
	CLD,
	STD,
	INC,
	DEC,
	SYSCALL,
	CMOVO,
	CMOVNO,
	CMOVB,
	CMOVNAE,
	CMOVC,
	CMOVNB,
	CMOVAE,
	CMOVNC,
	CMOVZ,
	CMOVE,
	CMOVNZ,
	CMOVNE,
	CMOVBE,
	CMOVNA,
	CMOVNBE,
	CMOVA,
	CMOVS,
	CMOVNS,
	CMOVP,
	CMOVPE,
	CMOVNP,
	CMOVPO,
	CMOVL,
	CMOVNGE,
	CMOVNL,
	CMOVGE,
	CMOVLE,
	CMOVNG,
	CMOVNLE,
	CMOVG,
	SETO,
	SETNO,
	SETB,
	SETNAE,
	SETC,
	SETNB,
	SETAE,
	SETNC,
	SETZ,
	SETE,
	SETNZ,
	SETNE,
	SETBE,
	SETNA,
	SETNBE,
	SETA,
	SETS,
	SETNS,
	SETP,
	SETPE,
	SETNP,
	SETPO,
	SETL,
	SETNGE,
	SETNL,
	SETGE,
	SETLE,
	SETNG,
	SETNLE,
	SETG,
	CPUID,
	BT,
	BTS,
	BTR,
	BTC,
	POPCNT,
	BSF,
	BSR,
	TZCNT,
	LZCNT,
	BSWAP,
	MOVBE;
	
	val string = name.lowercase()
}



enum class Width(val bytes: Int) {
	NONE(0),
	BYTE(1),
	WORD(2),
	DWORD(4),
	QWORD(8);

	val immWidth get() = if(this == QWORD) DWORD else this
	val string = name.lowercase()
	val opString = if(bytes == 0) "" else "$string "
	val min: Long = if(bytes > 8) 0 else -(1L shl ((bytes shl 3) - 1))
	val max: Long = if(bytes > 8) 0 else (1L shl ((bytes shl 3) - 1)) - 1
	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

	companion object {
		fun fromBytes(bytes: Int) = when(bytes) {
			1    -> BYTE
			2    -> WORD
			4    -> DWORD
			8    -> QWORD
			else -> null
		}
	}
}



enum class OpType(val width: Width = Width.NONE) {
	NONE,
	MEM,
	IMM,
	R8(Width.BYTE),
	R16(Width.WORD),
	R32(Width.DWORD),
	R64(Width.QWORD);
}



enum class Reg(val type: OpType, val index : Int) {

	AL(OpType.R8, 0), CL(OpType.R8, 1), DL(OpType.R8, 2), BL(OpType.R8, 3),
	AH(OpType.R8, 4), CH(OpType.R8, 5), DH(OpType.R8, 6), BH(OpType.R8, 7),
	R8B(OpType.R8, 8), R9B(OpType.R8, 9), R10B(OpType.R8, 10), R11B(OpType.R8, 11),
	R12B(OpType.R8, 12), R13B(OpType.R8, 13), R14B(OpType.R8, 14), R15B(OpType.R8, 15),
	SPL(OpType.R8, 4), BPL(OpType.R8, 5), SIL(OpType.R8, 6), DIL(OpType.R8, 7),

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
	NONE(OpType.NONE, 0);

	val string = name.lowercase()
	val value = (index and 0b111)
	val rex = (index shr 3) and 1
	val isInvalidIndex = index == 4
	val isImperfectBase = value == 5
	val width = type.width
	val noRex8 = type == OpType.R8 && (name == "AH" || name == "BH" || name == "CH" || name == "DH")
	val rex8 = type == OpType.R8 && (name == "SPL" || name == "BPL" || name == "SIL" || name == "DIL")
	val widthOrdinal = width.ordinal - 1

	companion object {
		val byteRegs = arrayOf(AL, CL, DL, BL, AH, CH, DH, BH, R8B, R9B, R10B, R11B, R12B, R13B, R14B, R15B)
		val wordRegs = arrayOf(AX, CX, DX, BX, SP, BP, SI, DI, R8W, R9W, R10W, R11W, R12W, R13W, R14W, R15W)
		val dwordRegs = arrayOf(EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI, R8D, R9D, R10D, R11D, R12D, R13D, R14D, R15D)
		val qwordRegs = arrayOf(RAX, RCX, RDX, RBX, RSP, RBP, RSI, RDI, R8, R9, R10, R11, R12, R13, R14, R15)
		val gpRegs = arrayOf(byteRegs, wordRegs, dwordRegs, qwordRegs)
		val aRegs = arrayOf(AL, AX, EAX, RAX)
		val cRegs = arrayOf(CL, CX, ECX, RCX)
		val dRegs = arrayOf(DL, DX, EDX, RDX)
		val r8Regs = arrayOf(R8B, R8W, R8D, R8)
		val r9Regs = arrayOf(R9B, R9W, R9D, R9)
		val argRegs = arrayOf(cRegs, dRegs, r8Regs, r9Regs)
		val arg64Regs = arrayOf(RCX, RDX, R8, R9)
		val argIndexes = intArrayOf(1, 2, 8, 9)
	}

}