package eyre.instructions

import java.nio.file.Files
import java.nio.file.Paths

fun main() {
	val lines = Files.readAllLines(Paths.get("instructions.txt"))

	for((index, line) in lines.withIndex()) {
		if(line.isEmpty() || line.startsWith(';')) continue
		if(line.startsWith('~')) break

		try {
			readLine(line)
		} catch(e: Exception) {
			System.err.println("Error on line ${index + 1}: $line")
			throw e
		}
	}
}



private fun readLine(line: String) {
	val beforeBrackets = line.substringBefore('[')
	if(beforeBrackets.length == line.length) return

	val firstSplit = beforeBrackets
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val mnemonicString = firstSplit[0]
	val operandsString = firstSplit[1]

	val parts = line
		.substring(beforeBrackets.length + 1, line.indexOf(']'))
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val extras = line
		.substringAfter(']')
		.trim()
		.split(',')
		.filter(String::isNotEmpty)

	readParts(mnemonicString, operandsString, parts, extras)
}



private val invalidExtras = setOf("NOLONG", "NEVER", "UNDOC", "OBSOLETE")

private val operandsMap = NasmOperands.values().associateBy { it.string }



private fun readParts(mnemonicString: String, operandsString: String, parts: List<String>, extras: List<String>) {
	if("sbyte" in operandsString) return
	if(extras.any(invalidExtras::contains)) return
	val operands = operandsMap[operandsString] ?: error("Unrecognised operands: $operandsString")


}



private enum class NasmOperands(val string: String) {
	M8_R8("mem,reg8"),
	M16_R16("mem,reg16"),
	M32_R32("mem,reg32"),
	M64_R64("mem,reg64"),
	R8_M8("reg8,mem"),
	R16_M16("reg16,mem"),
	R32_M32("reg32,mem"),
	R64_M64("reg64,mem"),
	R8_R8("reg8,reg8"),
	R16_R16("reg16,reg16"),
	R32_R32("reg32,reg32"),
	R64_R64("reg64,reg64"),
	AL_I("reg_al,imm"),
	AX_I("reg_ax,imm"),
	EAX_I("reg_eax,imm"),
	RAX_I("reg_rax,imm"),
	RM8_I8("rm8,imm"),
	RM16_I8("rm16,imm8"),
	RM32_I8("rm32,imm8"),
	RM64_I8("rm64,imm8"),
}


//ADC		mem,reg8			[mr:	hle 10 /r]				8086,SM,LOCK
//ADC		reg8,reg8			[mr:	10 /r]					8086
//ADC		mem,reg16			[mr:	hle o16 11 /r]				8086,SM,LOCK
//ADC		reg16,reg16			[mr:	o16 11 /r]				8086
//ADC		mem,reg32			[mr:	hle o32 11 /r]				386,SM,LOCK
//ADC		reg32,reg32			[mr:	o32 11 /r]				386
//ADC		mem,reg64			[mr:	hle o64 11 /r]				X86_64,LONG,SM,LOCK
//ADC		reg64,reg64			[mr:	o64 11 /r]				X86_64,LONG
//ADC		reg8,mem			[rm:	12 /r]					8086,SM
//ADC		reg8,reg8			[rm:	12 /r]					8086
//ADC		reg16,mem			[rm:	o16 13 /r]				8086,SM
//ADC		reg16,reg16			[rm:	o16 13 /r]				8086
//ADC		reg32,mem			[rm:	o32 13 /r]				386,SM
//ADC		reg32,reg32			[rm:	o32 13 /r]				386
//ADC		reg64,mem			[rm:	o64 13 /r]				X86_64,LONG,SM
//ADC		reg64,reg64			[rm:	o64 13 /r]				X86_64,LONG
//ADC		rm16,imm8			[mi:	hle o16 83 /2 ib,s]			8086,LOCK
//ADC		rm32,imm8			[mi:	hle o32 83 /2 ib,s]			386,LOCK
//ADC		rm64,imm8			[mi:	hle o64 83 /2 ib,s]			X86_64,LONG,LOCK
//ADC		reg_al,imm			[-i:	14 ib]					8086,SM
//ADC		reg_ax,sbyteword		[mi:	o16 83 /2 ib,s]				8086,SM,ND
//ADC		reg_ax,imm			[-i:	o16 15 iw]				8086,SM
//ADC		reg_eax,sbytedword		[mi:	o32 83 /2 ib,s]				386,SM,ND
//ADC		reg_eax,imm			[-i:	o32 15 id]				386,SM
//ADC		reg_rax,sbytedword		[mi:	o64 83 /2 ib,s]				X86_64,LONG,SM,ND
//ADC		reg_rax,imm			[-i:	o64 15 id,s]				X86_64,LONG,SM
//ADC		rm8,imm				[mi:	hle 80 /2 ib]				8086,SM,LOCK
//ADC		rm16,sbyteword			[mi:	hle o16 83 /2 ib,s]			8086,SM,LOCK,ND
//ADC		rm16,imm			[mi:	hle o16 81 /2 iw]			8086,SM,LOCK
//ADC		rm32,sbytedword			[mi:	hle o32 83 /2 ib,s]			386,SM,LOCK,ND
//ADC		rm32,imm			[mi:	hle o32 81 /2 id]			386,SM,LOCK
//ADC		rm64,sbytedword			[mi:	hle o64 83 /2 ib,s]			X86_64,LONG,SM,LOCK,ND
//ADC		rm64,imm			[mi:	hle o64 81 /2 id,s]			X86_64,LONG,SM,LOCK
//ADC		mem,imm8			[mi:	hle 80 /2 ib]				8086,SM,LOCK,ND
//ADC		mem,sbyteword16			[mi:	hle o16 83 /2 ib,s]			8086,SM,LOCK,ND
//ADC		mem,imm16			[mi:	hle o16 81 /2 iw]			8086,SM,LOCK
//ADC		mem,sbytedword32		[mi:	hle o32 83 /2 ib,s]			386,SM,LOCK,ND
//ADC		mem,imm32			[mi:	hle o32 81 /2 id]			386,SM,LOCK
//ADC		rm8,imm				[mi:	hle 82 /2 ib]				8086,SM,LOCK,ND,NOLONG

/*
private enum class NasmOperands(vararg val operands: NasmOperand) {
	NONE(NasmOperand.NONE)
}
private enum class NasmOperand(val string: String) {
	NONE("void"),
	R8("reg8"),
	M("mem"),
	R16("reg16"),
	R32("reg32"),
	R64("reg64"),
	I("imm"),
	I8("imm8"),
	AL("reg_al"),
	AX("reg_ax"),
	EAX("reg_eax"),
	RAX("reg_rax");
}*/
