package eyre

object Encs {


	private var pos = 0

	private var lineCount = 0

	private val mnemonicMap = Mnemonic.entries.associateBy { it.name }

	private val opsMap = Ops.entries.associateBy { it.name }

	private val missingMnemonics = LinkedHashSet<String>()

	private val groups = HashMap<Mnemonic, EncGroup>()

	private val zeroOpcodes = IntArray(Mnemonic.entries.size)

	init {
		parse()
		createZeroOpcodes()
	}

	operator fun get(mnemonic: Mnemonic) = groups[mnemonic]

	fun getZeroOpcode(mnemonic: Mnemonic) = zeroOpcodes[mnemonic.ordinal]



	private fun skipLine() {
		while(pos < encsString.length && encsString[pos++] != '\n') { }
	}

	private fun skipSpaces() {
		while(pos < encsString.length && encsString[pos] == ' ') pos++
	}

	private fun readPart(): String {
		val start = pos
		while(pos < encsString.length && !encsString[pos].isWhitespace())
			pos++
		return encsString.substring(start, pos)
	}



	private fun parse() {
		while(pos < encsString.length) {
			when(encsString[pos]) {
				'\n' -> { pos++; lineCount++ }
				' ', '#', '\t' -> skipLine()
				else -> parseLine()
			}
		}

		for(g in groups.values)
			g.encs.sortBy { it.ops }

		missingMnemonics.forEach(::println)
	}




	private fun createZeroOpcodes() {
		for(g in groups.values) {
			for(enc in g.encs) {
				if(enc.ops != Ops.NONE) continue
				var value = enc.opcode
				value = when(enc.prefix) {
					Prefix.NONE -> value
					Prefix.P66 -> (value shl 8) or 0x66
					Prefix.PF3 -> (value shl 8) or 0xF3
					Prefix.PF2 -> (value shl 8) or 0xF2
				}
				value = when(enc.escape) {
					Escape.NONE -> value
					Escape.E0F -> (value shl 8) or 0x0F
					Escape.E38 -> (value shl 16) or 0x380F
					Escape.E3A -> (value shl 16) or 0x3A0F
				}
				zeroOpcodes[g.mnemonic.ordinal] = value
			}
		}
	}



	private fun parseLine() {
		var ext = 0
		var opcode = 0
		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var mask = 0
		var rw = 0
		var o16 = 0
		var ops = Ops.NONE
		var mnemonicString = ""

		fun addOpcode(part: Int) {
			when(opcode) {
				0 -> when(part) {
					0x66 -> prefix = Prefix.P66
					0xF2 -> prefix = Prefix.PF2
					0xF3 -> prefix = Prefix.PF3
					0x0F -> escape = Escape.E0F
					else -> opcode = part
				}
				0x0F -> when(part) {
					0x38 -> escape = Escape.E38
					0x3A -> escape = Escape.E3A
					else -> opcode = part
				}
				else -> opcode = part
			}
		}

		while(pos < encsString.length && encsString[pos] != '\n') {
			val part = readPart()
			skipSpaces()

			if(part.length == 4 && part[2] == '/') {
				ext = part[3].digitToInt()
				addOpcode(part.dropLast(2).toInt(16))
			} else if(part.length == 2 && part[0].isHex && part[1].isHex) {
				addOpcode(part.toInt(16))
			} else if(mnemonicString.isEmpty()) {
				mnemonicString = part
			} else if(part.length == 4 && (part[0] == '0' || part[0] == '1')) {
				mask = part.toInt(2)
			} else when(part) {
				in opsMap -> ops = opsMap[part]!!
				"RW"    -> rw = 1
				"O16"   -> o16 = 1
				else    -> error("Invalid part: $part")
			}
		}

		fun add(opcode: Int, mnemonicString: String, ops: Ops) {
			val mnemonic = mnemonicMap[mnemonicString]
			if(mnemonic == null) { missingMnemonics += mnemonicString; return }
			val group = groups.getOrPut(mnemonic) { EncGroup(mnemonic) }
			if(ops.multi != null) {
				add(opcode, mnemonicString, ops.multi.first)
				add(opcode, mnemonicString, ops.multi.second)
			} else {
				if(ops !in group)
					group.add(Enc(prefix, escape, opcode, ext, mask, ops, rw, o16))
			}
		}

		if(mnemonicString.endsWith("cc")) {
			val trimmedMnemonic = mnemonicString.dropLast(2)
			for((suffix, offset) in ccList) {
				val opcode2 = if(opcode and 0xFF00 != 0) opcode + (offset shl 8) else opcode + offset
				val mnemonicString2 = trimmedMnemonic + suffix
				add(opcode2, mnemonicString2, ops)
			}
		} else {
			add(opcode, mnemonicString, ops)
		}
	}
}



private val ccList = mapOf(
	"O" to 0,
	"NO" to 1,
	"B" to 2, "NAE" to 2, "C" to 2,
	"NB" to 3, "AE" to 3, "NC" to 3,
	"Z" to 4, "E" to 4,
	"NZ" to 5, "NE" to 5,
	"BE" to 6, "NA" to 6,
	"NBE" to 7, "A" to 7,
	"S" to 8,
	"NS" to 9,
	"P" to 10, "PE" to 10,
	"NP" to 11, "PO" to 11,
	"L" to 12, "NGE" to 12,
	"NL" to 13, "GE" to 13,
	"LE" to 14, "NG" to 14,
	"NLE" to 15, "G" to 15
)

private const val encsString = """

80/0  ADD  RM_I   1111
00    ADD  RM_R   1111
02    ADD  R_RM   1111
80/1  OR   RM_I   1111
08    OR   RM_R   1111
0A    OR   R_RM   1111
80/2  ADC  RM_I   1111
10    ADC  RM_R   1111
12    ADC  R_RM   1111
80/3  SBB  RM_I   1111
18    SBB  RM_R   1111
1A    SBB  R_RM   1111
80/4  AND  RM_I   1111
20    AND  RM_R   1111
22    AND  R_RM   1111
80/5  SUB  RM_I   1111
28    SUB  RM_R   1111
2A    SUB  R_RM   1111
80/6  XOR  RM_I   1111
30    XOR  RM_R   1111
32    XOR  R_RM   1111
80/7  CMP  RM_I   1111
38    CMP  RM_R   1111
3A    CMP  R_RM   1111

FF/6   PUSH  RM   1010
50     PUSH  O    1010
68     PUSH  I32

8F/0   POP  RM  1010
58     POP  O   1010

63     MOVSXD  R_RM32  1100
0F BE  MOVSX   R_RM8   1110
0F BF  MOVSX   R_RM16  1100
0F B6  MOVZX   R_RM8   1110
0F B7  MOVZX   R_RM16  1100

F6/5   IMUL  RM       1111
0F AF  IMUL  R_RM     1110
69     IMUL  R_RM_I   1110

70     Jcc  REL8
0F 80  Jcc  REL32

F6/0  TEST  RM_I  1111
84    TEST  RM_R  1111
84    TEST  R_RM  1111

86  XCHG  R_RM  1111
86  XCHG  RM_R  1111

88  MOV  RM_R   1111
8A  MOV  R_RM   1111
B0  MOV  O_I    1111
C6  MOV  RM_I   1111

8D  LEA  R_MEM  1110

90       NOP
0F 1F/0  NOP  RM  1110

9B  WAIT
9B  FWAIT
F1  INT1
F1  ICEBP

9E  SAHF
9F  LAHF

98  CBW   O16
98  CWDE
98  CDQE  RW
99  CWD   O16
99  CDQ
99  CQO   RW

6C  INSB
6D  INSW  O16
6D  INSD
6E  OUTSB
6F  OUTSW  O16
6F  OUTSD
A4  MOVSB
A5  MOVSW  O16
A5  MOVSD
A5  MOVSQ  RW
A6  CMPSB
A7  CMPSW  O16
A7  CMPSD
A7  CMPSQ  RW
AA  STOSB
AB  STOSW  O16
AB  STOSD
AB  STOSQ  RW
AC  LODSB
AD  LODSW  O16
AD  LODSD
AD  LODSQ  RW
AE  SCASB
AF  SCASW  O16
AF  SCASD
AF  SCASQ  RW

C0/0  ROL  RM_I8   1111
D2/0  ROL  RM_CL   1111
C0/1  ROR  RM_I8   1111
D2/1  ROR  RM_CL   1111
C0/2  RCL  RM_I8   1111
D2/2  RCL  RM_CL   1111
C0/3  RCR  RM_I8   1111
D2/3  RCR  RM_CL   1111
C0/4  SAL  RM_I8   1111
D2/4  SAL  RM_CL   1111
C0/4  SHL  RM_I8   1111
D2/4  SHL  RM_CL   1111
C0/5  SHR  RM_I8   1111
D2/5  SHR  RM_CL   1111
C0/7  SAR  RM_I8   1111
D2/7  SAR  RM_CL   1111

C3  RET
C9  LEAVE
CC  INT3
CD  INT    I8
CF  IRET

E4  IN   A_I8  0111
EC  IN   A_DX  0111
E6  OUT  I8_A  0111
EE  OUT  DX_A  0111

E8    CALL   REL32
FF/2  CALL   RM     1000
FF/3  CALLF  M      1110

EB    JMP   REL8
E9    JMP   REL32
FF/4  JMP   RM     1000
FF/5  JMPF  M      1110

F4  HLT
F5  CMC

F6/2  NOT   RM  1111
F6/3  NEG   RM  1111
F6/4  MUL   RM  1111
F6/5  IMUL  RM  1111
F6/6  DIV   RM  1111
F6/7  IDIV  RM  1111

F8  CLC
F9  STC
FA  CLI
FB  STI
FC  CLD
FD  STD

FE/0  INC  RM  1111
FE/1  DEC  RM  1111

0F 05  SYSCALL

0F 40  CMOVcc  R_RM  1110

0F 90  SETcc  RM  0001

0F A2  CPUID

0F A3    BT   RM_R   1110
0F BA/4  BT   RM_I8  1110
0F AB    BTS  RM_R   1110
0F BA/5  BTS  RM_I8  1110
0F B3    BTR  RM_R   1110
0F BA/6  BTR  RM_I8  1110
0F BB    BTC  RM_R   1110
0F BA/7  BTC  RM_I8  1110

F3 0F B8  POPCNT  R_RM  1110

0F BC  BSF  R_RM  1110
0F BD  BSR  R_RM  1110

F3 0F BC  TZCNT  R_RM  1110
F3 0F BD  LZCNT  R_RM  1110

0F C8  BSWAP  O  1100

0F 38 F0  MOVBE  R_M  1110
0F 38 F1  MOVBE  M_R  1110

"""