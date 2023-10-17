package eyre.gen

import eyre.*
import java.nio.file.Files
import java.nio.file.Paths

class NasmParser(private val inputs: List<String>) {


	constructor(path: String) : this(Files.readAllLines(Paths.get(path)))



	val rawLines = ArrayList<RawNasmLine>()

	val filteredRawLines = ArrayList<RawNasmLine>()

	val lines = ArrayList<NasmLine>()

	val encs = ArrayList<NasmEnc>()
	
	val allEncs = ArrayList<NasmEnc>()

	val groups = ArrayList<NasmGroup>()

	val groupMap = HashMap<Mnemonic, NasmGroup>()
	


	fun parseAndConvert() {
		for(i in inputs.indices) readRawLine(i, inputs[i])?.let(rawLines::add)
		for(l in rawLines) if(filterLine(l)) filteredRawLines.add(l)
		for(l in filteredRawLines) scrapeLine(l, lines)
		for(l in lines) determineOperands(l)
		for(l in lines) convertLine(l, encs)
		encs.sortBy(NasmEnc::mnemonic)

		for(e in encs) {
			val group = groupMap[e.mnemonic] ?: NasmGroup(e.mnemonic).also {
				groupMap[e.mnemonic] = it
				groups.add(it)
			}

			val existing = group.encs.firstOrNull { it.opsString == e.opsString }
			if(existing != null && existing.avxOnly && e.avxOnly) continue
			group.encs.add(e)
			expandEnc(e, group.allEncs)
			allEncs.addAll(group.allEncs)
		}
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
		line.mnemonic in NasmLists.essentialMnemonics -> true
		(line.mnemonic == "PINSRB" || line.mnemonic == "PINSRW") && "mem" in line.operands -> false
		line.mnemonic == "PUSH" && line.operands[0] == "imm64" -> false
		"r+mi:" in line.parts -> false
		line.mnemonic == "aw" -> true
		"ND" in line.extras && line.operands[0] != "void" -> false
		line.mnemonic in NasmLists.invalidMnemonics -> false
		NasmLists.invalidExtras.any(line.extras::contains) -> false
		NasmLists.invalidOperands.any(line.operands::contains) -> false
		else -> true
	}



	private fun scrapeLine(raw: RawNasmLine, list: ArrayList<NasmLine>) {
		val line = NasmLine(raw)

		for(extra in raw.extras) when(extra) {
			"ND" -> line.nd = true
			in NasmLists.arches -> line.arch = NasmLists.arches[extra]!!
			in NasmLists.extensions -> line.extensions += NasmLists.extensions[extra]!!
			in NasmLists.opWidths -> line.opSize = NasmLists.opWidths[extra]!!
			in NasmLists.ignoredExtras -> continue
			"SM"  -> line.sm = true
			"SM2" -> line.sm = true
			"AR0" -> line.ar = 0
			"AR1" -> line.ar = 1
			"AR2" -> line.ar = 2
			else  -> raw.error("Invalid extra: $extra")
		}

		for(part in raw.parts) when {
			part in NasmLists.immTypes -> line.immType = NasmLists.immTypes[part]!!
			part in NasmLists.vsibs -> line.vsib = NasmLists.vsibs[part]!!
			part in NasmLists.ignoredParts -> continue
			part.startsWith("vex")  -> line.vex = part
			part.startsWith("evex") -> line.vex = part
			part.endsWith("+c")     -> { line.cc = true; line.addOpcode(part.dropLast(2).toInt(16)) }
			part.endsWith("+r")     -> { line.opreg = true; line.addOpcode(part.dropLast(2).toInt(16)) }
			part == "/r"   -> line.modrm  = true
			part == "/is4" -> line.is4    = true
			part == "o16"  -> line.o16    = 1
			part == "o64"  -> line.rexw   = 1
			part == "a32"  -> line.a32    = 1
			part == "odf"  -> line.odf    = true
			part == "f2i"  -> line.prefix = Prefix.PF2
			part == "f3i"  -> line.prefix = Prefix.PF3
			part == "wait" -> if(line.mnemonic == "FWAIT") line.opcode = 0x9B else line.prefix = Prefix.P9B
			part[0] == '/' -> if(line.mnemonic != "SETcc" && part != "/3r0") line.ext = part[1].digitToInt(10)

			part.contains(':') -> {
				val array = part.split(':').filter { it.isNotEmpty() }
				line.enc = NasmLists.opEncs[array[0]] ?: raw.error("Invalid ops: ${array[0]}")
				if(array.size > 1)
					line.tuple = NasmLists.tupleTypes[array[1]] ?: raw.error("Invalid tuple type")
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

		// Nasm encoding errors
		if(line.mnemonic == "PTWRITE")
			line.prefix = Prefix.PF3
		if(line.mnemonic == "VPBLENDVB")
			line.vexw = NasmVexW.W0

		val parts = (line.vex ?: return).split('.')

		for(part in parts) when(part) {
			"nds"  -> line.nds = true
			"ndd"  -> line.ndd = true
			"dds"  -> line.dds = true
			"vex"  -> line.isEvex = false
			"evex" -> line.isEvex = true
			"lz"   -> line.vexl = NasmVexL.LZ
			"l0"   -> line.vexl = NasmVexL.L0
			"l1"   -> line.vexl = NasmVexL.L1
			"lig"  -> line.vexl = NasmVexL.LIG
			"128"  -> line.vexl = NasmVexL.L128
			"256"  -> line.vexl = NasmVexL.L256
			"512"  -> line.vexl = NasmVexL.L512
			"wig"  -> line.vexw = NasmVexW.WIG
			"w0"   -> line.vexw = NasmVexW.W0
			"w1"   -> line.vexw = NasmVexW.W1
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

		if(line.mnemonic == "ENTER") {
			line.ops += NasmOp.I16
			line.ops += NasmOp.I8
			return
		}

		if(line.sm) {
			NasmLists.ops[strings[0]]?.width?.let { widths[1] = it }
			NasmLists.ops[strings[1]]?.width?.let { widths[0] = it }
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

				"imm64" -> when(line.mnemonic) {
					"XBEGIN" -> NasmOp.REL32
					"JMP"    -> NasmOp.REL32
					"CALL"   -> NasmOp.REL32
					else     -> NasmOp.I64
				}

				in NasmLists.ops -> NasmLists.ops[string]!!

				"xmmrm" -> when(widths[i]) {
					Width.DWORD -> NasmOp.XM32
					Width.QWORD -> NasmOp.XM64
					Width.XWORD -> NasmOp.XM128
					null        -> NasmOp.XM128
					else        -> line.raw.error("Invalid width: ${widths[i]}")
				}
				
				"mmxrm" -> when(widths[i]) {
					Width.QWORD -> NasmOp.MMM64
					Width.XWORD -> when(line.mnemonic) {
						"PMULUDQ", "PSUBQ" -> NasmOp.MMM64
						else -> line.raw.error("Invalid width: ${widths[i]}")
					}
					else -> line.raw.error("Invalid width: ${widths[i]}")
				}
				
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

				"mem_offs" -> when(widths[i]) {
					Width.BYTE  -> NasmOp.MOFFS8
					Width.WORD  -> NasmOp.MOFFS16
					Width.DWORD -> NasmOp.MOFFS32
					Width.QWORD -> NasmOp.MOFFS64
					else        -> line.raw.error("Invalid operand")
				}

				"imm", "imm|near", "imm|short" -> when(line.immType) {
					NasmImm.IB   -> NasmOp.I8
					NasmImm.IB_S -> NasmOp.I8
					NasmImm.IB_U -> NasmOp.I8
					NasmImm.IW   -> NasmOp.I16
					NasmImm.ID   -> NasmOp.I32
					NasmImm.ID_S -> NasmOp.I32
					NasmImm.IQ   -> NasmOp.I64
					NasmImm.REL8 -> NasmOp.REL8
					NasmImm.REL  -> if(line.odf)
						NasmOp.REL32
					else
						NasmOp.REL16
					else -> line.raw.error("Invalid width: ${line.immType}")
				}

				"reg32na" -> NasmOp.R32

				else -> line.raw.error("Unrecognised operand: $string")
			}
			
			line.ops += operand
		}
	}



	private fun NasmLine.toEnc(mnemonic: String, opcode: Int) = NasmEnc(
		null,
		NasmLists.mnemonics[mnemonic] ?: error("Missing mnemonic: $mnemonic"),
		prefix,
		escape,
		opcode,
		ext.coerceAtLeast(0),
		ext >= 0,
		extensions,
		enc,
		ArrayList<NasmOp>(ops),
		rexw,
		o16,
		a32,
		opreg,
		pseudo,
		enc in NasmLists.mrEncs,
		modrm,
		nd,
		vexw,
		vexl,
		tuple,
		sae,
		er,
		when { b16 -> 1; b32 -> 2; b64 -> 3; else -> 0 },
		k,
		z,
		vex != null,
		isEvex
	)



	private fun convertLine(line: NasmLine, list: ArrayList<NasmEnc>) {
		if(line.cc)
			for((postfix, opcodeInc) in ccList)
				list += line.toEnc(line.mnemonic.replace("cc", postfix), line.opcode + opcodeInc)
		else
			list += line.toEnc(line.mnemonic, line.opcode)
	}



	private fun expandEnc(enc: NasmEnc, list: ArrayList<NasmEnc>) {
		for((i, m) in enc.ops.withIndex()) {
			if(!m.isMulti) continue
			val enc1 = enc.copy(parent = enc, ops = ArrayList(enc.ops).also { it[i] = m.multi1 })
			val enc2 = enc.copy(parent = enc, ops = ArrayList(enc.ops).also { it[i] = m.multi2 })
			enc.children.add(enc1)
			enc.children.add(enc2)
			list.add(enc1)
			list.add(enc2)
			return
		}

		list += enc
	}


}