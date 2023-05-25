package eyre.instructions

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

	if(mnemonic in customMnemonics)
		return null

	if("ND" in extras && opString != "void")
		return null

	if(extras.any(Maps.invalidExtras::contains))
		return null

	if(opString.startsWith("mem,imm"))
		return null

	if (opString.contains("sbyte") ||
		opString.contains("fpureg|to") ||
		opString.contains("xmm0") ||
		opString.contains("imm64|near"))
		return null

	val result = NasmLine(lineNumber, mnemonic, opString, "$mnemonic $opString $parts $extras")

	for(extra in extras) when (extra) {
		in Maps.arches        -> result.arch      = Maps.arches[extra]!!
		in Maps.extensions    -> result.extension = Maps.extensions[extra]!!
		in Maps.opSizes       -> result.opSize    = Maps.opSizes[extra]!!
		in Maps.ignoredExtras -> continue
		"SM"  -> result.sm = true
		"SM2" -> result.sm = true
		"AR0" -> result.ar0 = true
		"AR1" -> result.ar1 = true
		"AR2" -> result.ar2 = true
		else  -> error("Invalid extra: $extra")
	}

	if(result.arch == Arch.FUTURE && result.extension == Extension.NONE)
		result.extension = Extension.NOT_GIVEN

	for(part in parts) when {
		part.startsWith("evex")   -> result.evex = part
		part.startsWith("vex")    -> result.vex = part
		part.endsWith("+c")       -> { result.cc = true; result.addOpcode(part.dropLast(2).toInt(16)) }
		part.endsWith("+r")       -> { result.opreg = true; result.addOpcode(part.dropLast(2).toInt(16)) }
		part == "/r"              -> result.hasModRM = true
		part == "/is4"            -> result.has4 = true
		part == "o16"             -> result.prefix = 0x66
		part == "o64"             -> result.rexw = true
		part == "a32"             -> result.prefix = 0x67
		part == "f2i"             -> result.prefix = 0xF2
		part == "f3i"             -> result.prefix == 0xF3
		part == "wait"            -> result.addOpcode(0x9B)
		part[0] == '/'            -> result.opcodeExt = part[1].digitToInt(10)
		part.endsWith(':')        -> result.encoding = part.dropLast(1)
		part in Maps.immWidths    -> result.immWidth = Maps.immWidths[part]!!
		part in Maps.opParts      -> result.opParts += Maps.opParts[part]!!
		part in Maps.ignoredParts -> continue
		part in Maps.vsibParts    -> result.vsibPart = Maps.vsibParts[part]!!

		part.length == 2 && part[0].isHex && part[1].isHex -> {
			if(result.hasModRM) {
				if(result.postModRM >= 0) error("Too many opcode parts")
				result.postModRM = part.toInt(16)
			} else {
				val value = part.toInt(16)
				when {
					result.evex != null || result.vex != null || result.oplen != 0
						-> result.addOpcode(value)
					value == 0x66 || value == 0xF2 || value == 0xF3
						-> result.prefix = value
					else
						-> result.addOpcode(value)
				}
			}
		}

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



private val extensions = setOf(
	Extension.NONE,
	Extension.FPU,
	Extension.SSE
)



fun main() {
	val rawLines = readLines()
	val groups = HashMap<String, NasmGroup>()
	val encodings = ArrayList<NasmEncoding>()
	val lines = rawLines//.filter { it.extension in extensions }

	for(line in lines) {
		groups.getOrPut(line.mnemonic) { NasmGroup(line.mnemonic) }.lines.add(line)
		determineOperands(line)
		encodings.addAll(processLine(line))
	}

	for(encoding in encodings)
		operands(encoding)

	Files.write(Paths.get("test.txt"), encodings.map(NasmEncoding::toString))
}



private fun processLine(line: NasmLine): List<NasmEncoding> {
	var compoundIndex = 0
	var compound: NasmOperand? = null

	for((index, operand) in line.operands.withIndex()) {
		if(operand.parts.isNotEmpty()) {
			if(compound != null)
				line.error("Multiple compound operands")
			compound = operand
			compoundIndex = index
		}
	}

	var op1 = line.operands.getOrNull(0)
	var op2 = line.operands.getOrNull(1)
	var op3 = line.operands.getOrNull(2)
	var op4 = line.operands.getOrNull(3)

	val list = ArrayList<NasmEncoding>()

	if(compound != null) {
		for(operand in compound.parts) {
			when(compoundIndex) {
				0 -> op1 = operand
				1 -> op2 = operand
				2 -> op3 = operand
				3 -> op4 = operand
			}

			list += line.toProcessedLine(line.mnemonic, op1, op2, op3, op4)
		}
	} else {
		list += line.toProcessedLine(line.mnemonic, op1, op2, op3, op4)
	}

	if(line.cc) {
		val list2 = ArrayList<NasmEncoding>()
		for(encoding in list) {
			for((i, mnemonics) in ccList.withIndex()) {
				for(mnemonic in mnemonics) {
					list2.add(encoding.copy(
						mnemonic = encoding.mnemonic.dropLast(2) + mnemonic,
						opcode = encoding.opcode + i
					))
				}
			}
		}
		return list2
	}

	return list
}



private fun operands(encoding: NasmEncoding) {
	val operands: NasmOperands? = when(encoding.operands.size) {
		1    -> operands1(encoding.op1!!)
		2    -> operands2(encoding.op1!!, encoding.op2!!).also {
			if(it == null) {
				if(encoding.operands.none { it.type == OperandType.S } && encoding.raw.evex == null && encoding.raw.vex == null)
				printUnique(encoding.operands.joinToString("_"))
			}
		}
		else -> null
	}
}



private fun operands1(op1: NasmOperand): NasmOperands? {
	return null
}



private fun operands2(op1: NasmOperand, op2: NasmOperand): NasmOperands? {
	val sameWidth = if(op2 == NasmOperand.I32 && op1.width == Width.QWORD)
		true
	else
		op1.width == op2.width && op1.width != null

	fun NasmOperands.sameWidth() = if(sameWidth) this else null

	if(op1 == NasmOperand.REL8) return when(op2) {
		NasmOperand.ECX -> NasmOperands.REL8_ECX
		NasmOperand.RCX -> NasmOperands.REL8_RCX
		else -> null
	}

	if(op1 == NasmOperand.ST && op2 == NasmOperand.ST0)
		return NasmOperands.ST_ST0
	if(op1 == NasmOperand.ST0 && op2 == NasmOperand.ST)
		return NasmOperands.ST0_ST

	if(op1 == NasmOperand.X) return when(op2) {
		NasmOperand.X -> NasmOperands.X_X
		NasmOperand.M128 -> NasmOperands.X_M
		else -> null
	}
	return when(op1.type) {
		OperandType.R -> when(op2.type) {
			OperandType.R -> NasmOperands.R_R.sameWidth()
			OperandType.M -> NasmOperands.R_M.sameWidth()
			OperandType.I -> {
				if(sameWidth)
					NasmOperands.R_I
				else if(op2 == NasmOperand.I8)
					NasmOperands.R_I8
				else
					null
			}
			OperandType.ONE -> NasmOperands.R_1
			OperandType.C -> NasmOperands.R_CL.takeIf { op2 == NasmOperand.CL }
			else -> null
		}

		OperandType.M -> when(op2.type) {
			OperandType.R -> NasmOperands.M_R.sameWidth()
			OperandType.I -> {
				if(sameWidth)
					NasmOperands.M_I
				else if(op2 == NasmOperand.I8)
					NasmOperands.M_I8
				else
					null
			}
			OperandType.ONE -> NasmOperands.M_1
			OperandType.C -> NasmOperands.M_CL.takeIf { op2 == NasmOperand.CL }
			else          -> null
		}

		OperandType.A -> when(op2.type) {
			OperandType.I -> NasmOperands.A_I.sameWidth()
			else -> null
		}

		else -> null
	}
}



private fun determineOperands(line: NasmLine) {
	val strings = line.opString.split(',')
	val widths = arrayOfNulls<Width>(4)

	if(line.sm) {
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

	when {
		line.ar0 -> widths[0] = width ?: if(line.mnemonic == "PUSH") null else line.error("No width")
		line.ar1 -> widths[1] = width ?: line.error("No width")
		line.ar2 -> widths[2] = width ?: line.error("No width")
	}

	if(!line.ar && !line.sm)
		widths.fill(width)

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

		val operand: NasmOperand = when(string) {
			"void" -> continue

			in Maps.operands -> Maps.operands[string]!!

			"mem" -> when(widths[i]) {
				null        -> NasmOperand.MEM
				Width.BYTE  -> NasmOperand.M8
				Width.WORD  -> NasmOperand.M16
				Width.DWORD -> NasmOperand.M32
				Width.QWORD -> NasmOperand.M64
				Width.TWORD -> NasmOperand.M80
				Width.XWORD -> NasmOperand.M128
				Width.YWORD -> NasmOperand.M256
				Width.ZWORD -> NasmOperand.M512
			}

			"xmmrm" -> when(widths[i]) {
				Width.DWORD -> NasmOperand.XM32
				Width.QWORD -> NasmOperand.XM64
				Width.XWORD -> NasmOperand.XM128
				null -> NasmOperand.XM128
				else -> line.error("Invalid width: ${widths[i]}")
			}

			"mmxrm" -> when(widths[i]) {
				Width.QWORD -> NasmOperand.MMM64
				Width.XWORD -> if(line.mnemonic == "PMULUDQ" || line.mnemonic == "PSUBQ")
					NasmOperand.MMM64
				else
					line.error("Invalid width: ${widths[i]}")
				else -> line.error("Invalid width: ${widths[i]}")
			}

			"imm", "imm|near" -> when(line.immWidth) {
				ImmWidth.IB   -> NasmOperand.I8
				ImmWidth.IB_S -> NasmOperand.I8
				ImmWidth.IB_U -> NasmOperand.I8
				ImmWidth.IW   -> NasmOperand.I16
				ImmWidth.ID   -> NasmOperand.I32
				ImmWidth.ID_S -> NasmOperand.I32
				ImmWidth.IQ   -> NasmOperand.I64
				ImmWidth.REL8 -> NasmOperand.REL8
				ImmWidth.REL  -> if(OpPart.ODF in line.opParts)
					NasmOperand.REL32
				else
					NasmOperand.REL16
				else -> line.error("Invalid width: ${line.immWidth}")
			}

			"imm|short" -> NasmOperand.I8

			else -> line.error("Unrecognised operand: $string")
		}

		line.operands += operand
	}
}