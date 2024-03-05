package eyre



sealed interface Mem

class GlobalMem(override var sec: Section, override var disp: Int) : Mem, Pos

class StackMem(var disp: Int): Mem

class RegMem(var reg: Reg) : Mem



enum class Mnemonic {
	ADD, OR, ADC, SBB, AND, SUB, XOR, CMP,
	PUSH, POP, MOVSXD, MOVSX, MOVZX, IMUL,
	JO, JNO, JB, JNAE, JC, JNB, JAE, JNC,
	JZ, JE, JNZ, JNE, JBE, JNA, JNBE, JA,
	JS, JNS, JP, JPE, JNP, JPO, JL, JNGE,
	JNL, JGE, JLE, JNG, JNLE, JG, TEST,
	XCHG, MOV, LEA, NOP, WAIT, INT1, SAHF,
	LAHF, CBW, CWDE, CDQE, CWD, CDQ, CQO,
	INSB, INSW, INSD, OUTSB, OUTSW, OUTSD,
	MOVSB, MOVSW, MOVSD, MOVSQ, CMPSB, CMPSW,
	CMPSD, CMPSQ, STOSB, STOSW, STOSD, STOSQ,
	LODSB, LODSW, LODSD, LODSQ, SCASB, SCASW,
	SCASD, SCASQ, ROL, ROR, RCL, RCR, SAL,
	SHL, SHR, SAR, RET, LEAVE, INT3, INT,
	IRET, IN, OUT, CALL, JMP, HLT, CMC, NOT,
	NEG, MUL, DIV, IDIV, CLC, STC, CLI, STI,
	CLD, STD, INC, DEC, SYSCALL, CMOVO,
	CMOVNO, CMOVB, CMOVNAE, CMOVC, CMOVNB,
	CMOVAE, CMOVNC, CMOVZ, CMOVE, CMOVNZ,
	CMOVNE, CMOVBE, CMOVNA, CMOVNBE, CMOVA,
	CMOVS, CMOVNS, CMOVP, CMOVPE, CMOVNP,
	CMOVPO, CMOVL, CMOVNGE, CMOVNL, CMOVGE,
	CMOVLE, CMOVNG, CMOVNLE, CMOVG, SETO,
	SETNO, SETB, SETNAE, SETC, SETNB, SETAE,
	SETNC, SETZ, SETE, SETNZ, SETNE, SETBE,
	SETNA, SETNBE, SETA, SETS, SETNS, SETP,
	SETPE, SETNP, SETPO, SETL, SETNGE, SETNL,
	SETGE, SETLE, SETNG, SETNLE, SETG, CPUID,
	BT, BTS, BTR, BTC, POPCNT, BSF, BSR,
	TZCNT, LZCNT, BSWAP, MOVBE;
	val string = name.lowercase()
}



enum class Width(val bytes: Int) {

	NONE(0),
	BYTE(1),
	WORD(2),
	DWORD(4),
	QWORD(8);

	val string = name.lowercase()
	val min: Long = if(bytes > 8) 0 else -(1L shl ((bytes shl 3) - 1))
	val max: Long = if(bytes > 8) 0 else (1L shl ((bytes shl 3) - 1)) - 1
	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

}



enum class OpType {
	NONE,
	R8,
	R16,
	R32,
	R64,
	MEM,
	IMM,;
}



@JvmInline
value class ValueReg(val backing: Int) {

	val typeOrdinal get() = backing shr 4
	val index get() = backing and 15
	val value get() = backing and 7
	val rex get() = (backing shr 3) and 1
	val isInvalidSibIndex get() = index == 4
	val isImperfectSibBase get() = value == 5
	val rex8 get() = value in 20..23
	val asR8 get() = ValueReg(16 or (backing and 7))
	val asR16 get() = ValueReg(32 or (backing and 7))
	val asR32 get() = ValueReg(48 or (backing and 7))
	val asR64 get() = ValueReg(64 or (backing and 7))
	val string get() = lowercaseNames.getOrElse(backing) { "invalid" }
	override fun toString() = string


	companion object {
		val argIndexes = intArrayOf(1, 2, 8, 9)
		
		private var counter = 16
		private fun reg() = ValueReg(counter++)
		val NONE = ValueReg(0)

		val AL   = ValueReg(16); val CL   = ValueReg(17); val DL   = ValueReg(18); val BL   = ValueReg(19)
		val SPL  = ValueReg(20); val BPL  = ValueReg(21); val SIL  = ValueReg(22); val DIL  = ValueReg(23)
		val R8B  = ValueReg(24); val R9B  = ValueReg(25); val R10B = ValueReg(26); val R11B = ValueReg(27)
		val R12B = ValueReg(28); val R13B = ValueReg(29); val R14B = ValueReg(30); val R15B = ValueReg(31)
		val AX   = ValueReg(32); val CX   = ValueReg(33); val DX   = ValueReg(34); val BX   = ValueReg(35)
		val SP   = ValueReg(36); val BP   = ValueReg(37); val SI   = ValueReg(38); val DI   = ValueReg(39)
		val R8W  = ValueReg(40); val R9W  = ValueReg(41); val R10W = ValueReg(42); val R11W = ValueReg(43)
		val R12W = ValueReg(44); val R13W = ValueReg(45); val R14W = ValueReg(46); val R15W = ValueReg(47)
		val EAX  = ValueReg(48); val ECX  = ValueReg(49); val EDX  = ValueReg(50); val EBX  = ValueReg(51)
		val ESP  = ValueReg(52); val EBP  = ValueReg(53); val ESI  = ValueReg(54); val EDI  = ValueReg(55)
		val R8D  = ValueReg(56); val R9D  = ValueReg(57); val R10D = ValueReg(58); val R11D = ValueReg(59)
		val R12D = ValueReg(60); val R13D = ValueReg(61); val R14D = ValueReg(62); val R15D = ValueReg(63)
		val RAX  = ValueReg(64); val RCX  = ValueReg(65); val RDX  = ValueReg(66); val RBX  = ValueReg(67)
		val RSP  = ValueReg(68); val RBP  = ValueReg(69); val RSI  = ValueReg(70); val RDI  = ValueReg(71)
		val R8   = ValueReg(72); val R9   = ValueReg(73); val R10  = ValueReg(74); val R11  = ValueReg(75)
		val R12  = ValueReg(76); val R13  = ValueReg(77); val R14  = ValueReg(78); val R15  = ValueReg(79)

		val names = arrayOf(
			"NONE", "INVALID", "INVALID", "INVALID", "INVALID", "INVALID", "INVALID", "INVALID",
			"INVALID", "INVALID", "INVALID", "INVALID", "INVALID", "INVALID", "INVALID", "INVALID",
			"AL", "CL", "DL", "BL", "SPL", "BPL", "SIL", "DIL",
			"R8B", "R9B", "R10B", "R11B", "R12B", "R13B", "R14B", "R15B",
			"AX", "CX", "DX", "BX", "SP", "BP", "SI", "DI",
			"R8W", "R9W", "R10W", "R11W", "R12W", "R13W", "R14W", "R15W",
			"EAX", "ECX", "EDX", "EBX", "ESP", "EBP", "ESI", "EDI",
			"R8D", "R9D", "R10D", "R11D", "R12D", "R13D", "R14D", "R15D",
			"RAX", "RCX", "RDX", "RBX", "RSP", "RBP", "RSI", "RDI",
			"R8", "R9", "R10", "R11", "R12", "R13", "R14", "R15"
		)

		val lowercaseNames = arrayOf(
			"none", "invalid", "invalid", "invalid", "invalid", "invalid", "invalid", "invalid",
			"invalid", "invalid", "invalid", "invalid", "invalid", "invalid", "invalid", "invalid",
			"al", "cl", "dl", "bl", "spl", "bpl", "sil", "dil",
			"r8b", "r9b", "r10b", "r11b", "r12b", "r13b", "r14b", "r15b",
			"ax", "cx", "dx", "bx", "sp", "bp", "si", "di",
			"r8w", "r9w", "r10w", "r11w", "r12w", "r13w", "r14w", "r15w",
			"eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi",
			"r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
			"rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi",
			"r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15"
		)
	}

}



enum class Reg(val type: OpType, val index: Int) {

	AL(OpType.R8, 0), CL(OpType.R8, 1), DL(OpType.R8, 2), BL(OpType.R8, 3),
	SPL(OpType.R8, 4), BPL(OpType.R8, 5), SIL(OpType.R8, 6), DIL(OpType.R8, 7),
	R8B(OpType.R8, 8), R9B(OpType.R8, 9), R10B(OpType.R8, 10), R11B(OpType.R8, 11),
	R12B(OpType.R8, 12), R13B(OpType.R8, 13), R14B(OpType.R8, 14), R15B(OpType.R8, 15),

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
	
	val width = when(type) {
		OpType.R8 -> Width.BYTE
		OpType.R16 -> Width.WORD
		OpType.R32 -> Width.DWORD
		OpType.R64 -> Width.QWORD
		else -> Width.NONE
	}

	val string = name.lowercase()
	val value = (index and 0b111)
	val rex = (index shr 3) and 1
	val isInvalidIndex = index == 4
	val isImperfectBase = value == 5
	val rex8 = type == OpType.R8 && index in 4..7
	val widthOrdinal = width.ordinal - 1

	companion object {
		val byteRegs = arrayOf(AL, CL, DL, BL, SPL, BPL, SIL, DIL, R8B, R9B, R10B, R11B, R12B, R13B, R14B, R15B)
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