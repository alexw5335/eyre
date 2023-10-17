package eyre

import eyre.gen.EncGen
import eyre.gen.NasmEnc

/**
 * Only designed to operate on complete data
 */
fun disasm(bytes: IntArray) {
	var pos = 0
	fun atEnd() = pos >= bytes.size

	// 66, 67, 9B, F0, F2, F3
	var prefixes = 0
	var rex = 0

	fun prefixOnly() {
		if(prefixes and 0b100 != 0)
			println("FWAIT")
		else
			error("Invalid encoding")
	}

	while(!atEnd()) {
		when(val byte = bytes[pos++]) {
			0x66 -> { if(prefixes and 1 != 0) prefixOnly(); prefixes = prefixes or 1 }
			0x67 -> { if(prefixes and 2 != 0) prefixOnly(); prefixes = prefixes or 2 }
			0x9B -> { if(prefixes and 4 != 0) prefixOnly(); prefixes = prefixes or 4}
			0xF0 -> { if(prefixes and 8 != 0) prefixOnly(); prefixes = prefixes or 8 }
			0xF2 -> { if(prefixes and 16 != 0) prefixOnly(); prefixes = prefixes or 16 }
			0xF3 -> { if(prefixes and 32 != 0) prefixOnly(); prefixes = prefixes or 32 }
			in 0x40..0x4F -> { if(rex != 0) prefixOnly(); rex = byte }
			else -> { pos--; break }
		}
	}

	if(atEnd()) prefixOnly()
	val opcode = bytes[pos++]
	val group = EncGen.disasmGroups[0][opcode]
	val rw = (rex shr 3) and 1
	fun match(enc: NasmEnc) = enc.prefixes and prefixes == enc.prefixes && enc.rw == rw

	if(group.modrm) {
		if(atEnd()) prefixOnly()
		val modrm = bytes[pos++]
		for(e in group.encs) {
			if(!match(e)) continue
			val mod = modrm shr 6
			val reg = (modrm shr 3) and 7
			val rm = modrm and 7
			if(group.ext && reg != e.ext) continue
		}
	} else {
		for(e in group.encs) {
			if(!match(e)) continue
			if(e.immWidth != null) {

			}
		}
	}
}



fun main() {
	EncGen
	//disasm(intArrayOf(0x41, 0x01, 0xC0))
}

//"C:\Program Files\Java\jdk-20.0.1\bin\java.exe" "-javaagent:C:\Users\Family\AppData\Local\Programs\IntelliJ IDEA Community Edition\lib\idea_rt.jar=51569:C:\Users\Family\AppData\Local\Programs\IntelliJ IDEA Community Edition\bin" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath "C:\Users\Family\Desktop\IDEA Projects\eyre\out\production\eyre;C:\Users\Family\.m2\repository\org\jetbrains\kotlin\kotlin-stdlib-jdk8\1.9.0\kotlin-stdlib-jdk8-1.9.0.jar;C:\Users\Family\.m2\repository\org\jetbrains\kotlin\kotlin-stdlib\1.9.0\kotlin-stdlib-1.9.0.jar;C:\Users\Family\.m2\repository\org\jetbrains\kotlin\kotlin-stdlib-common\1.9.0\kotlin-stdlib-common-1.9.0.jar;C:\Users\Family\.m2\repository\org\jetbrains\annotations\13.0\annotations-13.0.jar;C:\Users\Family\.m2\repository\org\jetbrains\kotlin\kotlin-stdlib-jdk7\1.9.0\kotlin-stdlib-jdk7-1.9.0.jar" eyre.DisassemblerKt

//NasmEnc(CLAC, NONE, E0F, CA01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(CLUI, PF3, E0F, EE01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(ENCLS, NONE, E0F, CF01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(ENCLU, NONE, E0F, D701, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(ENCLV, NONE, E0F, C001, /0, NONE, NONE, RW=0, O16=0, A32=0)

//NasmEnc(ENDBR32, PF3, E0F, FB1E, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(ENDBR64, PF3, E0F, FA1E, /0, NONE, NONE, RW=0, O16=0, A32=0)

//NasmEnc(F2XM1, NONE, NONE, F0D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FABS, NONE, NONE, E1D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FADD, NONE, NONE, C0D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FADD, NONE, NONE, C0DC, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FADD, NONE, NONE, C0D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FADD, NONE, NONE, C1DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FADDP, NONE, NONE, C0DE, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FADDP, NONE, NONE, C0DE, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FADDP, NONE, NONE, C1DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCHS, NONE, NONE, E0D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCLEX, P9B, NONE, E2DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVB, NONE, NONE, C0DA, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVB, NONE, NONE, C0DA, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVB, NONE, NONE, C1DA, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVBE, NONE, NONE, D0DA, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVBE, NONE, NONE, D0DA, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVBE, NONE, NONE, D1DA, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVE, NONE, NONE, C8DA, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVE, NONE, NONE, C8DA, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVE, NONE, NONE, C9DA, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNB, NONE, NONE, C0DB, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNB, NONE, NONE, C0DB, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNB, NONE, NONE, C1DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNBE, NONE, NONE, D0DB, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNBE, NONE, NONE, D0DB, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNBE, NONE, NONE, D1DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNE, NONE, NONE, C8DB, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNE, NONE, NONE, C8DB, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNE, NONE, NONE, C9DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNU, NONE, NONE, D8DB, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNU, NONE, NONE, D8DB, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVNU, NONE, NONE, D9DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVU, NONE, NONE, D8DA, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVU, NONE, NONE, D8DA, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCMOVU, NONE, NONE, D9DA, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCOM, NONE, NONE, D0D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOM, NONE, NONE, D0D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOM, NONE, NONE, D1D8, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCOMI, NONE, NONE, F0DB, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOMI, NONE, NONE, F0DB, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOMI, NONE, NONE, F1DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCOMIP, NONE, NONE, F0DF, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOMIP, NONE, NONE, F0DF, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOMIP, NONE, NONE, F1DF, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCOMP, NONE, NONE, D8D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOMP, NONE, NONE, D8D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FCOMP, NONE, NONE, D9D8, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCOMPP, NONE, NONE, D9DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FCOS, NONE, NONE, FFD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FDECSTP, NONE, NONE, F6D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FDISI, P9B, NONE, E1DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FDIV, NONE, NONE, F0D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FDIV, NONE, NONE, F8DC, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FDIV, NONE, NONE, F0D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FDIV, NONE, NONE, F9DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FDIVP, NONE, NONE, F8DE, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FDIVP, NONE, NONE, F8DE, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FDIVP, NONE, NONE, F9DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FDIVR, NONE, NONE, F0DC, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FDIVR, NONE, NONE, F8D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FDIVR, NONE, NONE, F8D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FDIVR, NONE, NONE, F1DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FDIVRP, NONE, NONE, F0DE, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FDIVRP, NONE, NONE, F0DE, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FDIVRP, NONE, NONE, F1DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FENI, P9B, NONE, E0DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FFREE, NONE, NONE, C0DD, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FFREE, NONE, NONE, C1DD, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FINCSTP, NONE, NONE, F7D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FINIT, P9B, NONE, E3DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLD, NONE, NONE, C0D9, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FLD, NONE, NONE, C1D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLD1, NONE, NONE, E8D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLDL2E, NONE, NONE, EAD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLDL2T, NONE, NONE, E9D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLDLG2, NONE, NONE, ECD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLDLN2, NONE, NONE, EDD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLDPI, NONE, NONE, EBD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FLDZ, NONE, NONE, EED9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FMUL, NONE, NONE, C8DC, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FMUL, NONE, NONE, C8D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FMUL, NONE, NONE, C8D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FMUL, NONE, NONE, C9DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FMULP, NONE, NONE, C8DE, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FMULP, NONE, NONE, C8DE, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FMULP, NONE, NONE, C9DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FNCLEX, NONE, NONE, E2DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FNDISI, NONE, NONE, E1DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FNENI, NONE, NONE, E0DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FNINIT, NONE, NONE, E3DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FNOP, NONE, NONE, D0D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FNSTSW, NONE, NONE, E0DF, /0, N, AX, RW=0, O16=0, A32=0)
//NasmEnc(FPATAN, NONE, NONE, F3D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FPREM, NONE, NONE, F8D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FPREM1, NONE, NONE, F5D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FPTAN, NONE, NONE, F2D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FRNDINT, NONE, NONE, FCD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSCALE, NONE, NONE, FDD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSETPM, NONE, NONE, E4DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSIN, NONE, NONE, FED9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSINCOS, NONE, NONE, FBD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSQRT, NONE, NONE, FAD9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FST, NONE, NONE, D0DD, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FST, NONE, NONE, D1DD, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSTP, NONE, NONE, D8DD, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FSTP, NONE, NONE, D9DD, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSTSW, P9B, NONE, E0DF, /0, N, AX, RW=0, O16=0, A32=0)
//NasmEnc(FSUB, NONE, NONE, E8DC, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FSUB, NONE, NONE, E0D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FSUB, NONE, NONE, E0D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FSUB, NONE, NONE, E9DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSUBP, NONE, NONE, E8DE, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FSUBP, NONE, NONE, E8DE, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FSUBP, NONE, NONE, E9DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSUBR, NONE, NONE, E0DC, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FSUBR, NONE, NONE, E8D8, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FSUBR, NONE, NONE, E8D8, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FSUBR, NONE, NONE, E1DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FSUBRP, NONE, NONE, E0DE, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FSUBRP, NONE, NONE, E0DE, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FSUBRP, NONE, NONE, E1DE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FTST, NONE, NONE, E4D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FUCOM, NONE, NONE, E0DD, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOM, NONE, NONE, E0DD, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOM, NONE, NONE, E1DD, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMI, NONE, NONE, E8DB, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMI, NONE, NONE, E8DB, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMI, NONE, NONE, E9DB, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMIP, NONE, NONE, E8DF, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMIP, NONE, NONE, E8DF, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMIP, NONE, NONE, E9DF, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMP, NONE, NONE, E8DD, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMP, NONE, NONE, E8DD, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMP, NONE, NONE, E9DD, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FUCOMPP, NONE, NONE, E9DA, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FXAM, NONE, NONE, E5D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FXCH, NONE, NONE, C8D9, /0, R, ST, RW=0, O16=0, A32=0)
//NasmEnc(FXCH, NONE, NONE, C8D9, /0, RN, ST_ST0, RW=0, O16=0, A32=0)
//NasmEnc(FXCH, NONE, NONE, C8D9, /0, NR, ST0_ST, RW=0, O16=0, A32=0)
//NasmEnc(FXCH, NONE, NONE, C9D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FXTRACT, NONE, NONE, F4D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FYL2X, NONE, NONE, F1D9, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(FYL2XP1, NONE, NONE, F9D9, /0, NONE, NONE, RW=0, O16=0, A32=0)

//NasmEnc(HRESET, PF3, E3A, C0F0, /0, IN, I8_EAX, RW=0, O16=0, A32=0)

//NasmEnc(LFENCE, NONE, E0F, E8AE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(MFENCE, NONE, E0F, F0AE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(MONITOR, NONE, E0F, C801, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(MWAIT, NONE, E0F, C901, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(PCONFIG, NONE, E0F, C501, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(PCONFIG, NONE, E0F, C501, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(RDMSRLIST, PF2, E0F, C601, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(RDPKRU, NONE, E0F, EE01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(RDTSCP, NONE, E0F, F901, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(SAVEPREVSSP, PF3, E0F, EA01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(SERIALIZE, NONE, E0F, E801, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(SETSSBSY, PF3, E0F, E801, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(SFENCE, NONE, E0F, F8AE, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(STAC, NONE, E0F, CB01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(STUI, PF3, E0F, EF01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(SWAPGS, NONE, E0F, F801, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(TESTUI, PF3, E0F, ED01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(TILERELEASE, NONE, E38, C049, /0, NONE, NONE, RW=0, O16=0, A32=0, W0, L128)
//NasmEnc(UIRET, PF3, E0F, EC01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(VMCALL, NONE, E0F, C101, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(VMFUNC, NONE, E0F, D401, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(VMLAUNCH, NONE, E0F, C201, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(VMRESUME, NONE, E0F, C301, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(VMXOFF, NONE, E0F, C401, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(WRMSRLIST, PF3, E0F, C601, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(WRMSRNS, NONE, E0F, C601, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(WRPKRU, NONE, E0F, EF01, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(XABORT, NONE, NONE, F8C6, /0, I, I8, RW=0, O16=0, A32=0)
//NasmEnc(XABORT, NONE, NONE, F8C6, /0, I, I8, RW=0, O16=0, A32=0)
//NasmEnc(XBEGIN, NONE, NONE, F8C7, /0, I, REL32, RW=0, O16=0, A32=0)
//NasmEnc(XBEGIN, NONE, NONE, F8C7, /0, I, REL32, RW=0, O16=0, A32=0)
//NasmEnc(XEND, NONE, E0F, D501, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(XGETBV, NONE, E0F, D001, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(XRESLDTRK, PF2, E0F, E901, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(XSETBV, NONE, E0F, D101, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(XSUSLDTRK, PF2, E0F, E801, /0, NONE, NONE, RW=0, O16=0, A32=0)
//NasmEnc(XTEST, NONE, E0F, D601, /0, NONE, NONE, RW=0, O16=0, A32=0)
//
//Process finished with exit code 0