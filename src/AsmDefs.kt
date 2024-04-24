package eyre


sealed interface VarLoc
class StackVarLoc(var disp: Int) : VarLoc
class RspVarLoc(var disp: Int) : VarLoc
class GlobalVarLoc(var reloc: SecPos) : VarLoc

sealed interface Operand
data class MemOperand(var base: Reg, var index: Reg, var scale: Int, var disp: Int) : Operand
data class RegOperand(var reg: Reg) : Operand
data class StackOperand(var disp: Int, var width: Width = Width.NONE) : Operand
data class RelocOperand(var reloc: SecPos, var disp: Int, var width: Width = Width.NONE) : Operand
data class ImmOperand(var value: Long) : Operand
data class Instruction(val mnemonic: Mnemonic, val op1: Operand?, val op2: Operand?, val op3: Operand?)

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
	operator fun contains(value: Reg) = value.type == ordinal
	operator fun contains(value: Int) = value in min..max
	operator fun contains(value: Long) = value in min..max

	companion object {
		fun fromSize(size: Int) = when(size) {
			1 -> BYTE
			2 -> WORD
			4 -> DWORD
			8 -> QWORD
			else -> error("Invalid width size: $size")
		}
	}

}



@JvmInline
value class Reg(val backing: Int) {

	constructor(type: Int, value: Int) : this((type shl 4) or value)

	val size get() = backing shr 4 // size in bytes
	val type get() = backing shr 4
	val index get() = backing and 15
	val value get() = backing and 7
	val rmValue get() = backing and 7
	val regValue get() = (backing and 7) shl 3

	val isInvalidSibIndex get() = index == 4
	val isImperfectSibBase get() = value == 5

	/** Reg */
	val rexR get() = (backing shr 1) and 4
	/** Index */
	val rexX get() = (backing shr 2) and 2
	/** RM, base, or opcode reg*/
	val rexB get() = (backing shr 3) and 1
	val rexW get() = (type and 4) shl 1 // assuming TYPE_R64 is 4
	val rex get() = (backing shr 3) and 1
	val hasRex get() = (backing and 0b1000) != 0
	val requiresRex get() = value in 20..23

	val asR8 get() = Reg(16 or (backing and 7))
	val asR16 get() = Reg(32 or (backing and 7))
	val asR32 get() = Reg(48 or (backing and 7))
	val asR64 get() = Reg(64 or (backing and 7))

	val isValid get() = backing >= TYPE_R8
	val isNone get() = backing shr 4 == TYPE_NONE
	val isR8 get() = backing shr 4 == TYPE_R8
	val isR16 get() = backing shr 4 == TYPE_R16
	val isR32 get() = backing shr 4 == TYPE_R32
	val isR64 get() = backing shr 4 == TYPE_R64

	val isVolatile get() = (0b00001111_00000111 and (1 shl index)) != 0
	val isNonVolatile get() = (0b00001111_00000111 and (1 shl index)) == 0

	fun ofSize(size: Int) = Reg(size.countTrailingZeroBits() + 1, value)

	override fun toString() = names.getOrElse(backing) { "invalid" }

	companion object {
		fun r8(index: Int) = Reg(16 or index)
		fun r16(index: Int) = Reg(32 or index)
		fun r32(index: Int) = Reg(48 or index)
		fun r64(index: Int) = Reg(64 or index)
		fun arg(index: Int) = when(index) { 0->RCX 1->RDX 2->R8 3->R9 else->NONE }
		fun ofSize(size: Int, value: Int) = Reg(size.countLeadingZeroBits() + 1, value)

		val NONE = Reg(0)
		const val TYPE_NONE = 0
		const val TYPE_R8 = 1
		const val TYPE_R16 = 2
		const val TYPE_R32 = 3
		const val TYPE_R64 = 4

		val AL   = Reg(16); val CL   = Reg(17); val DL   = Reg(18); val BL   = Reg(19)
		val SPL  = Reg(20); val BPL  = Reg(21); val SIL  = Reg(22); val DIL  = Reg(23)
		val R8B  = Reg(24); val R9B  = Reg(25); val R10B = Reg(26); val R11B = Reg(27)
		val R12B = Reg(28); val R13B = Reg(29); val R14B = Reg(30); val R15B = Reg(31)
		val AX   = Reg(32); val CX   = Reg(33); val DX   = Reg(34); val BX   = Reg(35)
		val SP   = Reg(36); val BP   = Reg(37); val SI   = Reg(38); val DI   = Reg(39)
		val R8W  = Reg(40); val R9W  = Reg(41); val R10W = Reg(42); val R11W = Reg(43)
		val R12W = Reg(44); val R13W = Reg(45); val R14W = Reg(46); val R15W = Reg(47)
		val EAX  = Reg(48); val ECX  = Reg(49); val EDX  = Reg(50); val EBX  = Reg(51)
		val ESP  = Reg(52); val EBP  = Reg(53); val ESI  = Reg(54); val EDI  = Reg(55)
		val R8D  = Reg(56); val R9D  = Reg(57); val R10D = Reg(58); val R11D = Reg(59)
		val R12D = Reg(60); val R13D = Reg(61); val R14D = Reg(62); val R15D = Reg(63)
		val RAX  = Reg(64); val RCX  = Reg(65); val RDX  = Reg(66); val RBX  = Reg(67)
		val RSP  = Reg(68); val RBP  = Reg(69); val RSI  = Reg(70); val RDI  = Reg(71)
		val R8   = Reg(72); val R9   = Reg(73); val R10  = Reg(74); val R11  = Reg(75)
		val R12  = Reg(76); val R13  = Reg(77); val R14  = Reg(78); val R15  = Reg(79)

		val names = arrayOf(
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