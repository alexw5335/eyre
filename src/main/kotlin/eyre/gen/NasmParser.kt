package eyre.gen

import eyre.*
import eyre.util.isHex

class NasmParser(private val inputs: List<String>) {


	val rawLines = ArrayList<RawNasmLine>()

	val filteredRawLines = ArrayList<RawNasmLine>()

	val lines = ArrayList<NasmLine>()

	val encs = ArrayList<NasmEnc>()



	fun read() {
		for(i in inputs.indices) readRawLine(i, inputs[i])?.let(rawLines::add)
		for(l in rawLines) if(filterLine(l)) filteredRawLines.add(l)
		for(l in filteredRawLines) scrapeLine(l, lines)
		for(l in lines) determineOperands(l)
		for(l in lines) convert(l, encs)
	}



	private fun RawNasmLine.error(message: String = "Misc. error"): Nothing {
		System.err.println("Error on line $lineNumber:")
		System.err.println("\t$this")
		System.err.println("\t$message")
		throw Exception()
	}



	private fun readRawLine(index: Int, input: String): RawNasmLine? {
		if(input.isEmpty() || input.startsWith(';')) return null

		val beforeBrackets = input.substringBefore('[')
		if(beforeBrackets.length == input.length) return null

		val firstSplit = beforeBrackets.split(' ', '\t').filter(String::isNotEmpty)
		val mnemonic = firstSplit[0]
		val operands = firstSplit[1].split(',')

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



	private fun filterLine(line: RawNasmLine) = when {
		line.mnemonic in EncGenLists.essentialMnemonics -> true
		(line.mnemonic == "PINSRB" || line.mnemonic == "PINSRW") && "mem" in line.operands -> false
		line.mnemonic == "PUSH" && line.operands[0] == "imm64" -> false
		"r+mi:" in line.parts -> false
		line.mnemonic == "aw" -> true
		"ND" in line.extras && line.operands[0] != "void" -> false
		line.mnemonic in EncGenLists.invalidMnemonics -> false
		EncGenLists.invalidExtras.any(line.extras::contains) -> false
		EncGenLists.invalidOperands.any(line.operands::contains) -> false
		else -> true
	}



	private fun scrapeLine(raw: RawNasmLine, list: ArrayList<NasmLine>) {
		val line = NasmLine(raw)

		for(extra in raw.extras) when(extra) {
			in EncGenLists.arches -> line.arch = EncGenLists.arches[extra]!!
			in EncGenLists.extensions -> line.extensions += EncGenLists.extensions[extra]!!
			in EncGenLists.opWidths -> line.opSize = EncGenLists.opWidths[extra]!!
			in EncGenLists.ignoredExtras -> continue
			"SM"  -> line.sm = true
			"SM2" -> line.sm = true
			"AR0" -> line.ar = 0
			"AR1" -> line.ar = 1
			"AR2" -> line.ar = 2
			else  -> raw.error("Invalid extra: $extra")
		}

		for(part in raw.parts) when {
			part in EncGenLists.immTypes -> line.immType = EncGenLists.immTypes[part]!!
			part in EncGenLists.vsibs -> line.vsib = EncGenLists.vsibs[part]!!
			part in EncGenLists.ignoredParts -> continue
			part.startsWith("vex")    -> line.vex = part
			part.startsWith("evex")   -> line.vex = part
			part.endsWith("+c")       -> { line.cc = true; line.addOpcode(part.dropLast(2).toInt(16)) }
			part.endsWith("+r")       -> { line.opreg = true; line.addOpcode(part.dropLast(2).toInt(16)) }
			part == "/r"   -> line.modrm  = true
			part == "/is4" -> line.is4    = true
			part == "o16"  -> line.o16    = 1
			part == "o64"  -> line.rexw   = 1
			part == "a32"  -> line.a32    = 1
			part == "odf"  -> line.odf    = true
			part == "f2i"  -> line.prefix = Prefix.PF2
			part == "f3i"  -> line.prefix = Prefix.PF3
			part == "wait" -> line.prefix = Prefix.P9B
			part[0] == '/' -> if(line.mnemonic != "SETcc") line.ext = part[1].digitToInt(10)

			part.contains(':') -> {
				val array = part.split(':').filter { it.isNotEmpty() }
				line.enc = EncGenLists.opEncs[array[0]] ?: raw.error("Invalid ops: ${array[0]}")
				if(array.size > 1)
					line.tuple = EncGenLists.tupleTypes[array[1]] ?: raw.error("Invalid tuple type")
				if(array.size == 3)
					line.vex = array[2]
			}

			part.length == 2 && part[0].isHex && part[1].isHex -> {
				if(line.modrm) {
					if(line.pseudo >= 0) error("Too many opcode parts")
					line.pseudo = part.toInt(16)
				} else {
					val value = part.toInt(16)
					when {
						line.vex != null ||
						line.opcode != 0 ||
						line.prefix != Prefix.NONE ||
						line.escape != Escape.NONE -> line.addOpcode(value)
						value == 0x66 -> line.prefix = Prefix.P66
						value == 0xF2 -> line.prefix = Prefix.PF2
						value == 0xF3 -> line.prefix = Prefix.PF3
						else  -> line.addOpcode(value)
					}
				}
			}

			else -> raw.error("Unrecognised opcode part: $part")
		}

		if(line.arch == NasmArch.FUTURE && line.extensions.isEmpty() && line.mnemonic.startsWith("K"))
			line.extensions += NasmExt.NOT_GIVEN

		list += line

		if(line.mnemonic == "PTWRITE")
			line.prefix = Prefix.PF3

		val parts = (line.vex ?: return).split('.')

		for(part in parts) when(part) {
			"nds"  -> line.nds = true
			"ndd"  -> line.ndd = true
			"dds"  -> line.dds = true
			"vex"  -> line.isEvex = false
			"evex" -> line.isEvex = true
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
			"0f"   -> line.escape = Escape.E0F
			"0f38" -> line.escape = Escape.E38
			"0f3a" -> line.escape = Escape.E3A
			"66"   -> line.prefix = Prefix.P66
			"f2"   -> line.prefix = Prefix.PF2
			"f3"   -> line.prefix = Prefix.PF3
			"np"   -> line.prefix = Prefix.NONE
			"map5" -> line.map5 = true
			"map6" -> line.map6 = true
			else   -> raw.error("Invalid vex part: $part")
		}
	}



	fun determineOperands(line: NasmLine) {
		val strings = line.raw.operands
		val widths = arrayOfNulls<Width>(4)

		if(line.sm) {
			EncGenLists.ops[strings[0]]?.width?.let { widths[1] = it }
			EncGenLists.ops[strings[1]]?.width?.let { widths[0] = it }
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

			val multi: NasmMultiOp? = when(string) {
				in EncGenLists.multiOps -> EncGenLists.multiOps[string]!!

				"xmmrm" -> when(widths[i]) {
					Width.DWORD -> NasmMultiOp.XM32
					Width.QWORD -> NasmMultiOp.XM64
					Width.XWORD -> NasmMultiOp.XM128
					null        -> NasmMultiOp.XM128
					else        -> line.raw.error("Invalid width: ${widths[i]}")
				}

				"mmxrm" -> when(widths[i]) {
					Width.QWORD -> NasmMultiOp.MMM64
					Width.XWORD -> if(line.mnemonic == "PMULUDQ" || line.mnemonic == "PSUBQ")
						NasmMultiOp.MMM64
					else
						line.raw.error("Invalid width: ${widths[i]}")
					else -> line.raw.error("Invalid width: ${widths[i]}")
				}
				
				else -> null
			}
			
			if(multi != null) {
				if(line.multiIndex >= 0) 
					line.raw.error("Multiple multi ops")
				line.multiIndex = line.ops.size
				line.multi = multi
				line.ops.add(Op.NONE)
				continue
			}

			val operand: Op = when(string) {
				"void" -> continue

				"imm64" -> when(line.mnemonic) {
					"XBEGIN" -> Op.REL32
					"JMP"    -> Op.REL32
					"CALL"   -> Op.REL32
					else     -> Op.I64
				}

				in EncGenLists.ops -> EncGenLists.ops[string]!!
				
				"mem" -> when(widths[i]) {
					null        -> Op.MEM
					Width.BYTE  -> Op.M8
					Width.WORD  -> Op.M16
					Width.DWORD -> Op.M32
					Width.QWORD -> Op.M64
					Width.TWORD -> Op.M80
					Width.XWORD -> Op.M128
					Width.YWORD -> Op.M256
					Width.ZWORD -> Op.M512
				}

				"mem_offs" -> when(widths[i]) {
					Width.BYTE  -> Op.MOFFS8
					Width.WORD  -> Op.MOFFS16
					Width.DWORD -> Op.MOFFS32
					Width.QWORD -> Op.MOFFS64
					else        -> line.raw.error("Invalid operand")
				}

				"imm", "imm|near", "imm|short" -> when(line.immType) {
					ImmType.IB   -> Op.I8
					ImmType.IB_S -> Op.I8
					ImmType.IB_U -> Op.I8
					ImmType.IW   -> Op.I16
					ImmType.ID   -> Op.I32
					ImmType.ID_S -> Op.I32
					ImmType.IQ   -> Op.I64
					ImmType.REL8 -> Op.REL8
					ImmType.REL  -> if(line.odf)
						Op.REL32
					else
						Op.REL16
					else -> line.raw.error("Invalid width: ${line.immType}")
				}

				"reg32na" -> Op.R32

				else -> line.raw.error("Unrecognised operand: $string")
			}

			line.ops += operand
		}
	}



	private fun convert(line: NasmLine, list: ArrayList<NasmEnc>) {
		fun add(mnemonic: String, opcode: Int) = list.add(NasmEnc(
			mnemonic,
			line.prefix,
			line.escape,
			opcode,
			line.ext.coerceAtLeast(0),
			line.ext >= 0,
			line.extensions,
			line.enc,
			ArrayList<Op>(line.ops),
			ArrayList<OpKind>(line.ops).also { if(line.multi != null) it[line.multiIndex] = line.multi!! },
			line.rexw,
			line.o16,
			line.pseudo,
			line.enc in EncGenLists.mrEncs,
			line.vexw,
			line.vexl,
			line.tuple,
			line.sae,
			line.er,
			when { line.b16 -> 1; line.b32 -> 2; line.b64 -> 3; else -> 0 },
			line.vsib,
			line.k,
			line.z,
			line.vex != null,
			line.isEvex
		))

		fun addMulti(mnemonic: String, opcode: Int) {
			if(line.multi != null) {
				line.ops[line.multiIndex] = line.multi!!.first
				add(mnemonic, opcode)
				line.ops[line.multiIndex] = line.multi!!.second
				add(mnemonic, opcode)
			} else {
				add(mnemonic, opcode)
			}
		}
		
		if(line.cc)
			for((postfix, opcodeInc) in EncGenLists.ccList)
				addMulti(line.mnemonic.dropLast(2) + postfix, line.opcode + opcodeInc)
		else
			addMulti(line.mnemonic, line.opcode)
	}


}