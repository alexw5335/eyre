package eyre.instructions

import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Paths



/*
Line reading
 */



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



private fun readLine(lineNumber: Int, line: String): NasmLine? {
	val beforeBrackets = line.substringBefore('[')
	if(beforeBrackets.length == line.length) return null

	val firstSplit = beforeBrackets.split(' ', '\t').filter(String::isNotEmpty)
	val mnemonic = firstSplit[0]
	val opString = firstSplit[1]

	val parts = line
		.substring(beforeBrackets.length + 1, line.indexOf(']'))
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val extras = line
		.substringAfter(']')
		.trim()
		.split(',')
		.filter(String::isNotEmpty)

	if("ND" in extras && opString != "void")
		return null

	if(extras.any(Maps.invalidExtras::contains))
		return null

	if(opString.startsWith("mem,imm"))
		return null

	if(opString.contains("sbyte") || opString.contains("fpureg|to") || opString.contains("xmm0"))
		return null

	val result = NasmLine(lineNumber, mnemonic, opString, "$mnemonic $opString $parts $extras")

	for(extra in extras) when (extra) {
		in Maps.arches        -> result.arch      = Maps.arches[extra]!!
		in Maps.extensions    -> result.extension = Maps.extensions[extra]!!
		in Maps.opSizes       -> result.opSize    = Maps.opSizes[extra]!!
		in Maps.sizeMatches   -> result.sizeMatch = Maps.sizeMatches[extra]!!
		in Maps.argMatches    -> result.argMatch  = Maps.argMatches[extra]!!
		in Maps.ignoredExtras -> continue
		else                  -> error("Invalid extra: $extra")
	}

	if(result.arch == Arch.FUTURE && result.extension == null)
		result.extension = Extension.NOT_GIVEN

	for(part in parts) when {
		part.startsWith("evex")   -> result.evex = part
		part.startsWith("vex")    -> result.vex = part
		part.endsWith("+c")       -> { result.plusC = true; result.opcodeParts.add(part.dropLast(2).toInt(16)) }
		part.endsWith("+r")       -> { result.plusR = true; result.opcodeParts.add(part.dropLast(2).toInt(16)) }
		part == "/r"              -> result.hasModRM = true
		part == "/is4"            -> result.has4 = true
		part[0] == '/'            -> result.ext = part[1].digitToInt(10)
		part.endsWith(':')        -> result.encoding = part.dropLast(1)
		part in Maps.immWidths    -> result.immWidth = Maps.immWidths[part]!!
		part in Maps.opParts      -> result.opParts += Maps.opParts[part]!!
		part in Maps.ignoredParts -> continue

		part in Maps.vsibParts  -> if(result.vsibPart != null)
			error("VSIB part already present")
		else
			result.vsibPart = Maps.vsibParts[part]!!

		part.length == 2 && part[0].isHex && part[1].isHex ->
			if(result.hasModRM)
				if(result.postModRM >= 0)
					error("Too many opcode parts")
				else
					result.postModRM = part.toInt(16)
			else
				result.opcodeParts.add(part.toInt(16))

		else -> if(!part.endsWith("w0") && !part.endsWith("w1")) error("Unrecognised opcode part: $part")
	}

	return result
}




private fun NasmLine.error(message: String = "Misc. error"): Nothing {
	System.err.println("Error on line $lineNumber:")
	System.err.println("\t$this")
	System.err.println("\t$message")
	throw IllegalStateException(message)
}



/*
Main
 */



fun main() {
	val lines = readLines()
	val groups = HashMap<String, NasmGroup>()

	for(line in lines)
		groups.getOrPut(line.mnemonic) { NasmGroup(line.mnemonic) }.lines.add(line)
}



private fun determineOperands(line: NasmLine) {
/*	Maps.explicitOperands[line.opString]?.let {
		line.set(it.first, it.second)
		return
	}

	if(line.sm) {
		Maps.smOperands[line.opString]?.let {
			line.set(it.first, it.second)
			return
		} ?: printUnique(line.opString)
	}*/

	val strings = line.opString.split(',')

	val widths = arrayOfNulls<Width>(4)

	if(line.sizeMatch != SizeMatch.NONE) {
		Maps.operands[strings[0]]?.width?.let { widths[1] = it }
		Maps.operands[strings[1]]?.width?.let { widths[0] = it }
	}

	val width = when(line.opSize) {
		OpSize.SB -> Width.BYTE
		OpSize.SW -> Width.WORD
		OpSize.SD -> Width.DWORD
		OpSize.SQ -> Width.QWORD
		OpSize.SO -> Width.XWORD
		OpSize.SY -> Width.YWORD
		OpSize.SZ -> Width.ZWORD
		else      -> null
	}

	when(line.argMatch) {
		ArgMatch.AR0 -> widths[0] = width ?: if(line.mnemonic == "PUSH") null else line.error("No width")
		ArgMatch.AR1 -> widths[1] = width ?: line.error("No width")
		ArgMatch.AR2 -> widths[2] = width ?: line.error("No width")
		ArgMatch.NONE -> { }
	}

	if(line.argMatch == ArgMatch.NONE && line.sizeMatch == SizeMatch.NONE)
		widths.fill(width)

	val trimmedStrings = ArrayList<String>()

	for(i in strings.indices) {
		var string = strings[i]

		if(string.endsWith("*"))     { line.star = true; string = string.dropLast(1) }
		if(string.endsWith("|z"))    { line.z    = true; string = string.dropLast(2) }
		if(string.endsWith("|mask")) { line.k = true; string = string.dropLast(5) }
		if(string.endsWith("|sae"))  { line.sae  = true; string = string.dropLast(4) }
		if(string.endsWith("|er"))   { line.er   = true; string = string.dropLast(3) }
		if(string.endsWith("|b16"))  { line.b16  = true; string = string.dropLast(4) }
		if(string.endsWith("|b32"))  { line.b32  = true; string = string.dropLast(4) }
		if(string.endsWith("|b64"))  { line.b64  = true; string = string.dropLast(4) }
		if(string.endsWith("|rs2"))  { line.rs2  = true; string = string.dropLast(4) }
		if(string.endsWith("|rs4"))  { line.rs4  = true; string = string.dropLast(4) }

		val operand: RawOperand = when(string) {
			in Maps.operands -> Maps.operands[string]!!

			"mem" -> when(widths[i]) {
				null        -> RawOperand.MEM
				Width.BYTE  -> RawOperand.M8
				Width.WORD  -> RawOperand.M16
				Width.DWORD -> RawOperand.M32
				Width.QWORD -> RawOperand.M64
				Width.TWORD -> RawOperand.M80
				Width.XWORD -> RawOperand.M128
				Width.YWORD -> RawOperand.M256
				Width.ZWORD -> RawOperand.M512
			}

			"xmmrm" -> when(widths[i]) {
				Width.DWORD -> RawOperand.XM32
				Width.QWORD -> RawOperand.XM64
				Width.XWORD -> RawOperand.XM128
				null -> RawOperand.XM128
				else -> line.error("Invalid width: ${widths[i]}")
			}

			"mmxrm" -> when(widths[i]) {
				Width.QWORD -> RawOperand.MMM64
				Width.XWORD -> if(line.mnemonic == "PMULUDQ" || line.mnemonic == "PSUBQ")
					RawOperand.MMM64
				else
					line.error("Invalid width: ${widths[i]}")
				else -> line.error("Invalid width: ${widths[i]}")
			}

			"imm" -> when(line.immWidth) {
				ImmWidth.IB   -> RawOperand.I8
				ImmWidth.IB_S -> RawOperand.I8
				ImmWidth.IB_U -> RawOperand.I8
				ImmWidth.IW   -> RawOperand.I16
				ImmWidth.ID   -> RawOperand.I32
				ImmWidth.ID_S -> RawOperand.I32
				ImmWidth.IQ   -> RawOperand.I64
				ImmWidth.REL8 -> RawOperand.REL8
				ImmWidth.REL  -> if(OpPart.ODF in line.opParts)
					RawOperand.REL32
				else
					RawOperand.REL16
				else -> line.error("Invalid width: ${line.immWidth}")
			}

			"imm|short" -> RawOperand.I8

			else -> line.error("Unrecognised operand: $string")
		}

		trimmedStrings += string
		line.operands += operand
	}

/*	val trimmedString = trimmedStrings.joinToString(",")

	Maps.explicitOperands[trimmedString]?.let {
		line.set(it.first, it.second)
		return
	}

	printUnique(trimmedString)*/

/*	for(operands in list) {
		if(operands.types.size != line.operands.size)
			continue

		var w: Width? = null

		for((i, t) in operands.types.withIndex()) {
			if(line.operands[i].type != t && line.operands[i] != t)
				continue
			if(t is OperandType && !t.fixedWidth) {
				if(w == null)
			}
		}

		return
	}

	printUnique(line.operands.joinToString("_"))*/
}