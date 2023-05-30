package eyre.nasm

import eyre.Width
import eyre.util.hexc8
import eyre.util.isHex
import java.nio.file.Files
import java.nio.file.Paths

@Suppress("unused")
class NasmReader(private val inputs: List<String>) {


	constructor(path: String) : this(Files.readAllLines(Paths.get(path)))



	val rawLines = ArrayList<RawNasmLine>()

	val lines = ArrayList<NasmLine>()

	val encodings = ArrayList<NasmEncoding>()



	fun readRawLines() = inputs.forEachIndexed { i, l -> readRawLine(i, l)?.let(rawLines::add) }

	fun filterLines() = rawLines.retainAll(::filterLine)

	fun scrapeLines() = rawLines.forEach { lines.add(scrapeLine(it)) }

	fun determineOperands() = lines.forEach { determineOperands(it) }

	fun filterExtensions(list: Set<NasmExt>) = lines.retainAll { list.containsAll(it.extensions) }

	fun convertLines() = lines.forEach { encodings += convertLine(it) }



	private fun RawNasmLine.error(message: String = "Misc. error"): Nothing {
		System.err.println("Error on line $lineNumber:")
		System.err.println("\t$this")
		System.err.println("\t$message")
		throw Exception()
	}



	fun readRawLine(index: Int, input: String): RawNasmLine? {
		if(input.isEmpty() || input.startsWith(';')) return null

		val beforeBrackets = input.substringBefore('[')
		if(beforeBrackets.length == input.length) return null

		val firstSplit = beforeBrackets.split(' ', '\t').filter(String::isNotEmpty)
		val mnemonic = firstSplit[0]
		val operands = firstSplit[1]

		val parts = input
			.substring(beforeBrackets.length + 1, input.indexOf(']'))
			.split(' ', '\t')
			.filter(String::isNotEmpty)

		val extras = input
			.substringAfter(']')
			.trim()
			.split(',')
			.filter(String::isNotEmpty)

		return RawNasmLine(index + 1, mnemonic, operands, parts, extras)
	}



	fun filterLine(line: RawNasmLine) = when {
		"r+mi:" in line.parts -> false
		line.mnemonic == "aw" -> true
		line.mnemonic in Maps.essentialMnemonics -> true
		"ND" in line.extras && line.operands != "void" -> false
		line.mnemonic in Maps.invalidMnemonics -> false
		Maps.invalidExtras.any(line.extras::contains) -> false
		Maps.invalidOperands.any(line.operands::contains) -> false
		else -> true
	}



	fun scrapeLine(raw: RawNasmLine): NasmLine {
		val line = NasmLine(raw)

		for(extra in raw.extras) when(extra) {
			in Maps.arches     -> line.arch = Maps.arches[extra]!!
			in Maps.extensions -> line.extensions += Maps.extensions[extra]!!
			in Maps.opWidths   -> line.opSize = Maps.opWidths[extra]!!
			in Maps.ignoredExtras   -> continue
			"SM"  -> line.sm = true
			"SM2" -> line.sm = true
			"AR0" -> line.ar = 0
			"AR1" -> line.ar = 1
			"AR2" -> line.ar = 2
			else  -> raw.error("Invalid extra: $extra")
		}

		for(part in raw.parts) when {
			part in Maps.immTypes     -> line.immType = Maps.immTypes[part]!!
			part in Maps.opParts      -> line.opParts += Maps.opParts[part]!!
			part in Maps.vsibs        -> line.vsib = Maps.vsibs[part]!!
			part in Maps.ignoredParts -> continue
			part.startsWith("evex")   -> line.evex = part
			part.startsWith("vex")    -> line.vex = part
			part.endsWith("+c")       -> { line.cc = true; line.addOpcode(part.dropLast(2).toInt(16)) }
			part.endsWith("+r")       -> { line.opreg = true; line.addOpcode(part.dropLast(2).toInt(16)) }
			part == "/r"    -> line.modrm  = true
			part == "/is4"  -> line.is4    = true
			part == "o16"   -> line.o16    = true
			part == "o64"   -> line.rexw   = true
			part == "a32"   -> line.a32    = true
			part == "f2i"   -> line.prefix = 0xF2
			part == "f3i"   -> line.prefix = 0xF3
			part == "wait"  -> line.prefix = 0x9B
			part[0] == '/'  -> line.opcodeExt = part[1].digitToInt(10)

			part.contains(':') -> {
				val array = part.split(':').filter { it.isNotEmpty() }
				line.opEnc = Maps.opEncs[array[0]] ?: raw.error("Invalid ops: ${array[0]}")
				if(array.size > 1)
					line.tupleType = Maps.tupleTypes[array[1]] ?: raw.error("Invalid tuple type")
				if(array.size == 3)
					line.evex = array[2]
			}

			part.length == 2 && part[0].isHex && part[1].isHex -> {
				if(line.modrm) {
					if(line.postModRM >= 0) error("Too many opcode parts")
					line.postModRM = part.toInt(16)
				} else {
					val value = part.toInt(16)
					when {
						line.evex != null || line.vex != null || line.oplen != 0
						-> line.addOpcode(value)
						value == 0x66 || value == 0xF2 || value == 0xF3
						-> line.prefix = value
						else
						-> line.addOpcode(value)
					}
				}
			}

			else -> raw.error("Unrecognised opcode part: $part")
		}

		if(line.arch == NasmArch.FUTURE && line.extensions.isEmpty() && line.mnemonic.startsWith("K"))
			line.extensions += NasmExt.NOT_GIVEN

		val parts = (line.vex ?: line.evex ?: return line).split('.')

		for(part in parts) when(part) {
			"nds",
			"ndd",
			"dds",
			"vex",
			"evex" -> continue
			"lz"   -> line.vexl = VexL.LZ
			"l0"   -> line.vexl = VexL.L0
			"l1"   -> line.vexl = VexL.L1
			"lig"  -> line.vexl = VexL.LIG
			"128"  -> line.vexl = VexL.L128
			"256"  -> line.vexl = VexL.L256
			"512"  -> line.vexl = VexL.L512
			"wig"  -> line.vexw = VexW.WIG
			"w0"   -> line.vexw = VexW.W0
			"w1"   -> line.vexw = VexW.W1
			"0f"   -> line.vexExt = VexExt.E0F
			"0f38" -> line.vexExt = VexExt.E38
			"0f3a" -> line.vexExt = VexExt.E3A
			"66"   -> line.vexPrefix = VexPrefix.P66
			"f2"   -> line.vexPrefix = VexPrefix.PF2
			"f3"   -> line.vexPrefix = VexPrefix.PF3
			"np"   -> line.vexPrefix = VexPrefix.NP
			"map5" -> line.map5 = true
			"map6" -> line.map6 = true
			else   -> raw.error("Invalid vex part: $part")
		}

		return line
	}



	fun determineOperands(line: NasmLine) {
		val strings = line.raw.operands.split(',')
		val widths = arrayOfNulls<Width>(4)

		if(line.sm) {
			Maps.operands[strings[0]]?.width?.let { widths[1] = it }
			Maps.operands[strings[1]]?.width?.let { widths[0] = it }
		}

		if(line.ar >= 0)
			widths[line.ar] = line.opSize ?: if(line.mnemonic == "PUSH") null else line.raw.error("No width")

		if(line.ar < 0 && !line.sm) widths.fill(line.opSize)

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

			val operand: NasmOp = when(string) {
				"void" -> continue

				in Maps.operands -> Maps.operands[string]!!

				"mem" -> when(widths[i]) {
					null        -> NasmOp.MEM
					Width.BYTE  -> NasmOp.M8
					Width.WORD  -> NasmOp.M16
					Width.DWORD -> NasmOp.M32
					Width.QWORD -> NasmOp.M64
					Width.TWORD -> NasmOp.M80
					Width.XWORD -> NasmOp.M128
					Width.YWORD -> NasmOp.M256
					Width.ZWORD -> NasmOp.M512
				}

				"xmmrm" -> when(widths[i]) {
					Width.DWORD -> NasmOp.XM32
					Width.QWORD -> NasmOp.XM64
					Width.XWORD -> NasmOp.XM128
					null -> NasmOp.XM128
					else -> line.raw.error("Invalid width: ${widths[i]}")
				}

				"mmxrm" -> when(widths[i]) {
					Width.QWORD -> NasmOp.MMM64
					Width.XWORD -> if(line.mnemonic == "PMULUDQ" || line.mnemonic == "PSUBQ")
						NasmOp.MMM64
					else
						line.raw.error("Invalid width: ${widths[i]}")
					else -> line.raw.error("Invalid width: ${widths[i]}")
				}

				"mem_offs" -> when(widths[i]) {
					Width.BYTE  -> NasmOp.MOFFS8
					Width.WORD  -> NasmOp.MOFFS16
					Width.DWORD -> NasmOp.MOFFS32
					Width.QWORD -> NasmOp.MOFFS64
					else        -> line.raw.error("Invalid operand")
				}

				"imm", "imm|near" -> when(line.immType) {
					ImmType.IB   -> NasmOp.I8
					ImmType.IB_S -> NasmOp.I8
					ImmType.IB_U -> NasmOp.I8
					ImmType.IW   -> NasmOp.I16
					ImmType.ID   -> NasmOp.I32
					ImmType.ID_S -> NasmOp.I32
					ImmType.IQ   -> NasmOp.I64
					ImmType.REL8 -> NasmOp.REL8
					ImmType.REL  -> if(OpPart.ODF in line.opParts)
						NasmOp.REL32
					else
						NasmOp.REL16
					else -> line.raw.error("Invalid width: ${line.immType}")
				}

				"imm|short" -> NasmOp.I8
				"reg32na" -> NasmOp.R32

				else -> line.raw.error("Unrecognised operand: $string")
			}

			line.addOperand(operand)
		}
	}



	private fun combineOperands(line: NasmLine) : Pair<NasmOps, Width?> {
		outer@ for(operands in NasmOps.values) {
			if(line.operands.size != operands.parts.size) continue

			var width: Width? = null

			for((i, opClass) in operands.parts.withIndex()) {
				val operand = line.operands[i]
				when(opClass) {
					is NasmOpType ->
						if(operand.type != opClass)
							continue@outer
						else if(width == null)
							width = operand.width
						else if(operand.width != width && !(operand == NasmOp.I32 && width == Width.QWORD))
							continue@outer
					is NasmOp ->
						if(operand != opClass)
							continue@outer
				}
			}

			return operands to width
		}

		//printUnique(line.operands.joinToString("_")); return Operands.M to null
		line.raw.error("Invalid operands: ${line.operands.joinToString()}")
	}



	private fun convertLine(line: NasmLine): List<NasmEncoding> {
		val list = ArrayList<NasmEncoding>()

		fun add() {
			val (operands, width) = combineOperands(line)
			if(line.cc)
				for((mnemonic, opcode) in Maps.ccList)
					list += line.toEncoding(line.mnemonic.dropLast(2) + mnemonic, line.addedOpcode(opcode), operands, width)
			else
				list += line.toEncoding(line.mnemonic, line.opcode, operands, width)
		}

		if(line.compound != null) {
			for(operand in line.compound!!.parts) {
				line.operands[line.compoundIndex] = operand
				add()
			}
			line.operands[line.compoundIndex] = line.compound!!
		} else {
			add()
		}

		return list
	}



	private fun NasmLine.toEncoding(
		mnemonic: String,
		opcode: Int,
		operands: NasmOps,
		width: Width?
	) = NasmEncoding(
		this,
		mnemonic,
		opcodeExt,
		opcode,
		oplen,
		prefix,
		rexw,
		o16,
		a32,
		operands,
		width,
		this.operands
	)


}