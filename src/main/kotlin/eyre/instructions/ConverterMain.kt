package eyre.instructions

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess



/*
Line reading
 */



private fun readLine(lineNumber: Int, line: String): NasmLine? {
	val beforeBrackets = line.substringBefore('[')
	if(beforeBrackets.length == line.length) return null

	val firstSplit = beforeBrackets
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val mnemonicString = firstSplit[0]
	val operandsString = firstSplit[1]

	val operands = operandsString.split(',')

	val parts = line
		.substring(beforeBrackets.length + 1, line.indexOf(']'))
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val extras = line
		.substringAfter(']')
		.trim()
		.split(',')
		.filter(String::isNotEmpty)

	if(extras.any(invalidExtras::contains)) return null

	for(e in extras)
		if(e !in Maps.arches && e !in Maps.extensions && e !in Maps.extras && e !in ignoredExtras)
			error("Invalid extra: $e")

	if("sbyte" in operandsString) return null

	val arch        = extras.firstNotNullOfOrNull(Maps.arches::get)
	val extension   = extras.firstNotNullOfOrNull(Maps.extensions::get)
	val sizesString = extras.mapNotNull(Maps.sizes::get).joinToString("_")
	val size        = if(sizesString.isEmpty()) null else Maps.sizes[sizesString] ?: error(sizesString)
	var immWidth: ImmWidth? = null
	val opcodeParts = ArrayList<Int>()
	var plusC = false
	var plusR = false
	var evex: String? = null
	var vex: String? = null
	var ext = -1
	var hasModRM = false
	var has4 = false
	var encoding = ""
	var postModRM = -1
	var vsibPart: VsibPart? = null
	val opParts = ArrayList<OpPart>()

	for(part in parts) when {
		part.startsWith("evex") -> evex = part
		part.startsWith("vex")  -> vex = part
		part.endsWith("+c")     -> { plusC = true; opcodeParts.add(part.dropLast(2).toInt(16)) }
		part.endsWith("+r")     -> { plusR = true; opcodeParts.add(part.dropLast(2).toInt(16)) }
		part == "/r"            -> hasModRM = true
		part == "/is4"          -> has4 = true
		part[0] == '/'          -> ext = part[1].digitToInt(10)
		part.endsWith(':')      -> encoding = part.dropLast(1)
		part in Maps.immWidths  -> immWidth = Maps.immWidths[part]!!
		part in Maps.opParts    -> opParts += Maps.opParts[part]!!
		part in ignoredParts    -> { }

		part in Maps.vsibParts  -> if(vsibPart != null)
			error("VSIB part already present")
		else
			vsibPart = Maps.vsibParts[part]!!

		part.length == 2 && part[0].isHex && part[1].isHex ->
			if(hasModRM)
				if(postModRM >= 0)
					error("Too many opcode parts")
				else
					postModRM = part.toInt(16)
			else
				opcodeParts.add(part.toInt(16))

		else -> if(!part.endsWith("w0") && !part.endsWith("w1")) error("Unrecognised opcode part: $part")
	}

	if(opParts.size > 1) println(opParts)

	return NasmLine(
		lineNumber,
		mnemonicString,
		operandsString,
		operands,
		parts,
		extras,
		arch,
		extension,
		size,
		immWidth
	)
}



private fun readLines(): List<NasmLine> {
	val lines = Files.readAllLines(Paths.get("instructions.txt"))
	val nasmLines = ArrayList<NasmLine>()

	for((index, line) in lines.withIndex()) {
		if(line.isEmpty() || line.startsWith(';')) continue
		if(line.startsWith('~')) break

		try {
			readLine(index + 1, line)?.let(nasmLines::add)
		} catch(e: Exception) {
			System.err.println("Error on line ${index + 1}: $line")
			throw e
		}
	}

	return nasmLines
}



private val lines = readLines()



/*
Main
 */



fun main() {
	//for(line in lines)
	//	line.determineOperands()

	Files.write(Paths.get("instructions3.txt"), lines.map(NasmLine::toString))
}



private fun NasmLine.error(message: String = "Misc. error"): Nothing {
	System.err.println("Error on line $lineNumber:")
	System.err.println("\t$this")
	System.err.println("\t$message")
	exitProcess(1)
}



private fun NasmLine.determineOperands(): NasmOperands {
	fun invalid(): Nothing = error("Unrecognised operands: $operandsString")

	if(size != null && size.sm) return when(operandsString) {
		"mem,reg8"    -> NasmOperands.M8_R8
		"mem,reg16"   -> NasmOperands.M16_R16
		"mem,reg32"   -> NasmOperands.M32_R32
		"mem,reg64"   -> NasmOperands.M64_R64
		"reg8,mem"    -> NasmOperands.R8_M8
		"reg16,mem"   -> NasmOperands.R16_M16
		"reg32,mem"   -> NasmOperands.R32_M32
		"reg64,mem"   -> NasmOperands.R64_M64
		"reg_al,imm"  -> NasmOperands.AL_I8
		"reg_ax,imm"  -> NasmOperands.AX_I16
		"reg_eax,imm" -> NasmOperands.EAX_I32
		"reg_rax,imm" -> NasmOperands.RAX_I32
		"rm8,imm"     -> NasmOperands.RM8_I8
		"rm16,imm"    -> NasmOperands.RM16_I16
		"rm32,imm"    -> NasmOperands.RM32_I32
		"rm64,imm"    -> NasmOperands.RM64_I32
		"mem,imm8"    -> NasmOperands.M8_I8
		"mem,imm16"   -> NasmOperands.M16_I16
		"mem,imm32"   -> NasmOperands.M32_I32
		"reg16,mem,imm8"   -> NasmOperands.R16_M16_I8
		"reg16,mem,imm16"  -> NasmOperands.R16_M16_I16
		"reg16,mem,imm"    -> NasmOperands.R16_M16_I16
		"reg16,reg16,imm"  -> NasmOperands.R16_R16_I16
		"reg32,mem,imm8"   -> NasmOperands.R32_M32_I8
		"reg32,mem,imm32"  -> NasmOperands.R32_M32_I32
		"reg32,mem,imm"    -> NasmOperands.R32_M32_I32
		"reg32,reg32,imm"  -> NasmOperands.R32_R32_I32
		"reg64,mem,imm8"   -> NasmOperands.R64_M64_I8
		"reg64,mem,imm32"  -> NasmOperands.R64_M64_I32
		"reg64,mem,imm"    -> NasmOperands.R64_M64_I32
		"reg64,reg64,imm"  -> NasmOperands.R64_R64_I32
		"reg16,imm"        -> NasmOperands.R16_I16
		"reg32,imm"        -> NasmOperands.R32_I32
		"reg64,imm"        -> NasmOperands.R64_I32
		"reg_al,mem_offs"  -> NasmOperands.AL_MOFFS
		"reg_ax,mem_offs"  -> NasmOperands.AX_MOFFS
		"reg_eax,mem_offs" -> NasmOperands.EAX_MOFFS
		"reg_rax,mem_offs" -> NasmOperands.RAX_MOFFS
		"mem_offs,reg_al"  -> NasmOperands.MOFFS_AL
		"mem_offs,reg_ax"  -> NasmOperands.MOFFS_AX
		"mem_offs,reg_eax" -> NasmOperands.MOFFS_EAX
		"mem_offs,reg_rax" -> NasmOperands.MOFFS_RAX
		"reg8,imm"         -> NasmOperands.R8_I8
		"mem,reg16,imm"    -> NasmOperands.M16_R16_I16
		"mem,reg32,imm"    -> NasmOperands.M32_R32_I32
		"mem,reg64,imm"    -> NasmOperands.M64_R64_I32
		"mem,reg16,reg_cl" -> NasmOperands.M16_R16_CL
		"mem,reg32,reg_cl" -> NasmOperands.M32_R32_CL
		"mem,reg64,reg_cl" -> NasmOperands.M64_R64_CL
		"reg16,mem16" -> NasmOperands.R16_M16
		"reg32,mem32" -> NasmOperands.R32_M32
		"reg64,mem64" -> NasmOperands.R64_M64
		"mem16,reg16" -> NasmOperands.M16_R16
		"mem32,reg32" -> NasmOperands.M32_R32
		"mem64,reg64" -> NasmOperands.M64_R64
		else -> invalid()
	}

/*	if(mnemonic == "IN") return when(operandsString) {
		"reg_al,imm"     -> NasmOperands.AL_I8
		"reg_ax,imm"     -> NasmOperands.AX_I8
		"reg_eax,imm"    -> NasmOperands.EAX_I8
		"reg_al,reg_dx"  -> NasmOperands.AL_DX
		"reg_ax,reg_dx"  -> NasmOperands.AX_DX
		"reg_eax,reg_dx" -> NasmOperands.EAX_DX
		else -> error()
	}

	if(mnemonic == "OUT") return when(operandsString) {
		"imm,reg_al"     -> NasmOperands.I8_AL
		"imm,reg_ax"     -> NasmOperands.I8_AX
		"imm,reg_eax"    -> NasmOperands.I8_EAX
		"reg_dx,reg_al"  -> NasmOperands.DX_AL
		"reg_dx,reg_ax"  -> NasmOperands.DX_AX
		"reg_dx,reg_eax" -> NasmOperands.DX_EAX
		else -> error()
	}

	smMap[operandsString]?.let {
		if(!size.sm)
			error("Invalid operands")
		return it
	}

	noneMap[operandsString]?.let {
		return it
	}*/

	return NasmOperands.R64_M64

	//error("invalid operands")
}
