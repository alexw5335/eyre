package eyre.instructions

import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Paths



/*
Line reading
 */



private fun readLine(lineNumber: Int, line: String): NasmLine? {
	val beforeBrackets = line.substringBefore('[')
	if(beforeBrackets.length == line.length) return null

	val firstSplit = beforeBrackets
		.split(' ', '\t')
		.filter(String::isNotEmpty)

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

	if(mnemonic in customMnemonics)
		return null

	if("ND" in extras && opString != "void")
		return null

	if(extras.any(Maps.invalidExtras::contains))
		return null

	if(opString.contains("sbyte") || opString.contains("fpureg|to"))
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
	for(line in lines)
		determineOperands(line)

	val list = lines.map { with(it) { "$mnemonic ${operands.joinToString("_")}" } }
	Files.write(Paths.get("instructions4.txt"), list)
}



private fun NasmLine.error(message: String = "Misc. error"): Nothing {
	System.err.println("Error on line $lineNumber:")
	System.err.println("\t$this")
	System.err.println("\t$message")
	throw IllegalStateException(message)
}



private fun determineOperands(line: NasmLine) {
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

	for(i in strings.indices) {
		var string = strings[i]

		if(string.endsWith("*"))
			string = string.dropLast(1)

		if(string.endsWith("|z")) {
			line.zeroMask = true
			string = string.dropLast(2)
		}

		if(string.endsWith("|mask")) {
			line.vecMask = true
			string = string.dropLast(5)
		}

		if(string.endsWith("|sae")) {
			line.sae = true
			string = string.dropLast(4)
		}

		if(string.endsWith("|er")) {
			line.er = true
			string = string.dropLast(3)
		}

		if(string.endsWith("|b16")) {
			line.b16 = true
			string = string.dropLast(4)
		}

		if(string.endsWith("|b32")) {
			line.b32 = true
			string = string.dropLast(4)
		}

		if(string.endsWith("|b64")) {
			line.b64 = true
			string = string.dropLast(4)
		}

		if(string.endsWith("|rs2")) {
			line.rs2 = true
			string = string.dropLast(4)
		}

		if(string.endsWith("|rs4")) {
			line.rs4 = true
			string = string.dropLast(4)
		}

		val operand: Operand = when(string) {
			in Maps.operands -> Maps.operands[string]!!
			"mem" -> when(widths[i]) {
				null        -> Operand.M
				Width.BYTE  -> Operand.M8
				Width.WORD  -> Operand.M16
				Width.DWORD -> Operand.M32
				Width.QWORD -> Operand.M64
				Width.TWORD -> Operand.M80
				Width.XWORD -> Operand.M128
				Width.YWORD -> Operand.M256
				Width.ZWORD -> Operand.M512
			}
			"xmmrm" -> when(widths[i]) {
				Width.DWORD -> Operand.XM32
				Width.QWORD -> Operand.XM64
				Width.XWORD -> Operand.XM128
				null -> Operand.XM128
				else -> line.error("Invalid width: ${widths[i]}")
			}
			"mmxrm" -> when(widths[i]) {
				Width.QWORD -> Operand.MMM64
				Width.XWORD -> if(line.mnemonic == "PMULUDQ" || line.mnemonic == "PSUBQ")
					Operand.MMM64
				else
					line.error("Invalid width: ${widths[i]}")
				else -> line.error("Invalid width: ${widths[i]}")
			}
			"imm" -> when(line.immWidth) {
				ImmWidth.IB,
				ImmWidth.IB_S,
				ImmWidth.IB_U -> Operand.I8
				ImmWidth.IW   -> Operand.I16
				ImmWidth.ID,
				ImmWidth.ID_S -> Operand.I32
				ImmWidth.IQ   -> Operand.I64
				ImmWidth.REL8 -> Operand.REL8
				ImmWidth.REL  -> if(OpPart.ODF in line.opParts)
					Operand.REL32
				else
					Operand.REL16
				else -> line.error("Invalid width: ${line.immWidth}")
			}
			"imm|short" -> Operand.I8
			else -> line.error("Unrecognised operand: $string")
		}

		line.operands += operand
	}
}