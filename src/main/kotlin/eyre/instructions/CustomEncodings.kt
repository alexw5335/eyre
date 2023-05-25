package eyre.instructions



val customMnemonics = setOf(
	// 16-bit encodings
	"ENTER",
	"LEAVE",

	// Unique encodings
	"IN",
	"OUT",
	"MOV",

	// Opreg
	"XCHG",
	"BSWAP",

	// Opreg, imm, 16-bit encodings
	"PUSH",
	"POP",

	// Far
	"JMP",
	"CALL",

	// Mismatched register widths
	"MOVSX",
	"MOVZX",
	"MOVSXD",

	// Mismatched register widths
	"LEA",
	"LAR",
	"LSL",
	"LGS",
	"LFS",
	"LSS",

	// Obsolete
	"JMPE",

	// Not found in Intel manuals?
	"CMPccXADD",
)



val customEncodings = """

E4  IN   A_I   0111
EC  IN   A_DX  0111
E6  OUT  I_A   0111
EE  OUT  DD_A  0111

EB    JMP   REL8
E9    JMP   REL32
FF/4  JMP   RM     1000
FF/5  JMPF  M      1110

E8    CALL   REL32
FF/2  CALL   RM  1000
FF/3  CALLF  M  0111

C8     ENTER   I16_I8
66 C8  ENTERW  I16_I8
C9     LEAVE
66 C9  LEAVEW

88  MOV  RM_R     1111
8B  MOV  R_RM     1111
8C  MOV  R_SEG    1110
8C  MOV  M_SEG    0010
8E  MOV  SEG_R    1110
8E  MOV  SEG_M    0010
A0  MOV  A_MOFFS  1111
A2  MOV  MOFFS_A  1111
B0  MOV  O_I      1111
C6  MOV  RM_I     1111

0F 21    MOV  R_DR   1000
0F 23    MOV  DR_R   1000
0F 20    MOV  R_CR   1000
0F 20/0  MOV  R_CR8  1000
0F 22    MOV  CR_R   1000
0F 22/0  MOV  CR8_R  1000

FF/6   PUSH  RM   1010
50     PUSH  O    1010
6A     PUSH  I8
66 68  PUSH  I16
68     PUSH  I32

0F A0  PUSH  FS
0F A8  PUSH  GS
66 0F A0  PUSHW  FS
66 0F A8  PUSHW  GS

8F/0  POP  RM  1010
58    POP  O   1010

0F A1  POP FS
0F A9  POP GS
66 0F 1A  POPW  FS
66 0F 1A  POPW  GS

0F C8  BSWAP  O  1100

90  XCHG  A_O   1110
90  XCHG  O_A   1110
86  XCHG  RM_R  1110
86  XCHG  R_RM  1110

0F BE  MOVSX  R_RM8   1110
0F BF  MOVSX  R_RM16  1100
0F B6  MOVZX  R_RM8   1110
0F B7  MOVZX  R_RM16  1100

63  MOVSXD  R64_RM32  0111

8D  LEA  R_MEM  1110

0F B4  LFS  R_MEM  1110
0F B5  LGS  R_MEM  1110
0F B2  LSS  R_MEM  1110

66 0F 02  LAR  R16_R32
0F 02     LAR  R32_M16
0F 02     LAR  R32_R16
0F 02     LAR  R64_M16
0F 02     LAR  R64_R16
0F 02     LAR  R64_R32
66 0F 03  LSL  R16_R32
0F 03     LSL  R32_M16
0F 03     LSL  R32_R16
0F 03     LSL  R64_M16
0F 03     LSL  R64_R16
0F 03     LSL  R64_R32



"""