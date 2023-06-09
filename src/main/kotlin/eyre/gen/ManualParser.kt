package eyre.gen

import eyre.*
import java.nio.file.Files
import java.nio.file.Paths
import eyre.util.isHex
import eyre.Width.*

class ManualParser(private val inputs: List<String>) {


	constructor(path: String) : this(Files.readAllLines(Paths.get(path)))


	private val opsMap = Ops.values().associateBy { it.name }

	private val multiMap = MultiOps.values().associateBy { it.name }

	private val mnemonicMap = Mnemonic.values().associateBy { it.name }



	fun parse(): List<Encoding> {
		val encodings = ArrayList<Encoding>()
		for(i in inputs.indices)
			readLine(i, inputs[i], encodings)
		return encodings
	}

	fun convert(encodings: List<Encoding>) = ArrayList<CommonEncoding>().also {
		for(e in encodings) convert(e, it)
	}



	private fun readLine(lineNumber: Int, input: String, list: ArrayList<Encoding>) {
		if(input.isEmpty() || input.startsWith(';')) return

		val parts = input.split(' ').filter { it.isNotEmpty() }

		var pos = 0

		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var opcode = 0
		var ext = 0
		var mask1 = OpMask(0)
		var mask2 = OpMask(0)
		var opsString = "NONE"
		var rexw = 0
		var o16 = 0
		val mnemonic: String
		var norexw = 0
		var pseudo = -1

		while(true) {
			var part = parts[pos++]

			if('/' in part) {
				ext = part.last().digitToInt()
				part = part.substring(0..1)
			} else if(part.length > 2 || !part[0].isHex || !part[1].isHex) {
				mnemonic = part
				break
			}

			val value = part.toInt(16)

			if(opcode == 0) {
				when(value) {
					0x66 -> if(escape == Escape.NONE) prefix = Prefix.P66 else opcode = 0x66
					0xF2 -> if(escape == Escape.NONE) prefix = Prefix.PF2 else opcode = 0xF2
					0xF3 -> if(escape == Escape.NONE) prefix = Prefix.PF3 else opcode = 0xF3
					0x9B -> if(escape == Escape.NONE) prefix = Prefix.P9B else opcode = 0x9B
					0x67 -> if(escape == Escape.NONE) prefix = Prefix.P67 else opcode = 0x67
					0x0F -> if(escape == Escape.NONE) escape = Escape.E0F else opcode = 0x0F
					0x38 -> if(escape == Escape.E0F) escape = Escape.E38 else opcode = 0x38
					0x3A -> if(escape == Escape.E0F) escape = Escape.E3A else opcode = 0x3A
					0x00 -> if(escape == Escape.E0F) escape = Escape.E00 else opcode = 0x00
					else -> opcode = value
				}
			} else {
				opcode = opcode or (value shl 8)
			}
		}

		if(mnemonic == "WAIT") {
			opcode = 0x9B
			prefix = Prefix.NONE
		}

		if(pos < parts.size && parts[pos] != "RW" && parts[pos] != "O16")
			opsString = parts[pos++]

		while(pos < parts.size) {
			when(val part = parts[pos++]) {
				"RW"   -> rexw = 1
				"O16"  -> o16 = 1
				"NORW" -> norexw = 1
				else   ->
					if(part.startsWith(":"))
						pseudo = part.drop(1).toInt()
					else
						mask1 = OpMask(part.toIntOrNull(2) ?: error("Line $lineNumber: Invalid part: $part"))
			}
		}

		fun add(mnemonic: String, opcode: Int, ops: Ops, prefix: Prefix) = list.add(Encoding(
			mnemonicMap[mnemonic] ?: error("Unrecognised mnemonic: $mnemonic"),
			prefix,
			escape,
			opcode,
			mask1,
			mask2,
			ext,
			ops,
			rexw,
			o16,
			norexw,
			pseudo
		))

		if(opsString == "E_EM") {
			add(mnemonic, opcode, Ops.MM_MM, prefix)
			add(mnemonic, opcode, Ops.MM_M, prefix)
			add(mnemonic, opcode, Ops.X_X, Prefix.P66)
			add(mnemonic, opcode, Ops.X_M, Prefix.P66)
		} else if(opsString == "E_I8") {
			add(mnemonic, opcode, Ops.MM_I8, prefix)
			add(mnemonic, opcode, Ops.X_I8, Prefix.P66)
		} else if(opsString == "E_EM_I8") {
			add(mnemonic, opcode, Ops.MM_MM_I8, prefix)
			add(mnemonic, opcode, Ops.MM_M_I8, prefix)
			add(mnemonic, opcode, Ops.X_X_I8, Prefix.P66)
			add(mnemonic, opcode, Ops.X_M_I8, Prefix.P66)
		} else {
			val multi = multiMap[opsString]
			val ops = opsMap[opsString]

			if(multi != null) {
				if(multi.mask != null) {
					mask2 = mask1
					mask1 = multi.mask
				} else if(multi.mask2 != null) {
					mask2 = multi.mask2
				}
			}

			if(multi == null && ops == null)
				error("Line $lineNumber: Unrecognised ops: $opsString")

			if(mnemonic.endsWith("cc")) {
				for((postfix, opcodeInc) in Maps.ccList)
					if(multi != null)
						for(part in multi.parts)
							add(mnemonic.dropLast(2) + postfix, opcode + opcodeInc, part, prefix)
					else
						add(mnemonic.dropLast(2) + postfix, opcode + opcodeInc, ops!!, prefix)
			} else {
				if(multi != null)
					for(part in multi.parts)
						add(mnemonic, opcode, part, prefix)
				else
					add(mnemonic, opcode, ops!!, prefix)
			}
		}
	}



	private val multiOpMap = mapOf(
		"RM"   to Pair("R", "M"),
		"RM8"  to Pair("R8", "M8"),
		"RM16" to Pair("R16", "M16"),
		"RM32" to Pair("R32", "M32"),
		"RM64" to Pair("R64", "M64"),
		"XM"   to Pair("X", "M128"),
		"MMM"  to Pair("MM", "M64"),
		"XM16" to Pair("X", "M16"),
		"XM32" to Pair("X", "M32"),
		"XM64" to Pair("X", "M64"),
		"BNDM128" to Pair("BND", "M128")
	)
	
	
	private val Width.r get() = when(this) {
		BYTE  -> Op.R8
		WORD  -> Op.R16
		DWORD -> Op.R32
		QWORD -> Op.R64
		else  -> error("Invalid width: $this")
	}

	private val Width.i get() = when(this) {
		BYTE  -> Op.I8
		WORD  -> Op.I16
		DWORD -> Op.I32
		QWORD -> Op.I32
		else  -> error("Invalid width: $this")
	}

	private val Width.a get() = when(this) {
		BYTE  -> Op.AL
		WORD  -> Op.AX
		DWORD -> Op.EAX
		QWORD -> Op.RAX
		else  -> error("Invalid width: $this")
	}

	private val Width.m get() = when(this) {
		BYTE  -> Op.M8
		WORD  -> Op.M16
		DWORD -> Op.M32
		QWORD -> Op.M64
		TWORD -> Op.M80
		XWORD -> Op.M128
		YWORD -> Op.M256
		ZWORD -> Op.M512
	}
	
	
	
	fun convert(encoding: Encoding, list: ArrayList<CommonEncoding>) {
		fun add(ops: List<Op>, rexw: Int, o16: Int, opcode: Int) {
			list.add(CommonEncoding(
				encoding.mnemonic.name,
				encoding.prefix,
				encoding.escape,
				opcode,
				encoding.ext,
				ops,
				rexw,
				o16,
				encoding.pseudo
			))
		}

		fun add(ops: List<Op>) = add(ops, encoding.rexw, encoding.o16, encoding.opcode)

		fun add(vararg ops: Op) = add(ops.toList())

		var found = false
		when(encoding.ops) {
			Ops.NONE -> add()
			Ops.RA,
			Ops.RA_M512  -> encoding.mask.forEachWidth { add(it.r) }
			Ops.MM_MM    -> add(Op.MM, Op.MM)
			Ops.MM_I8    -> add(Op.MM, Op.I8)
			Ops.MM_MM_I8 -> add(Op.MM, Op.MM, Op.I8)
			Ops.X_MM     -> add(Op.X, Op.MM)
			Ops.MM_X     -> add(Op.MM, Op.X)
			Ops.X_X      -> add(Op.X, Op.X)
			Ops.X_I8     -> add(Op.X, Op.I8)
			Ops.X_X_I8   -> add(Op.X, Op.X, Op.I8)
			else         -> found = false
		}
		if(found) return
		
		encoding.mask.forEachWidth { with(it) {
			val rexw = if(encoding.rexw == 1)
				1
			else if(this == QWORD && encoding.norexw == 0 && DWORD in encoding.mask)
				1
			else
				0

			val o16 = if(encoding.o16 == 1)
				1
			else if(this == WORD && encoding.mask != OpMask.WORD)
				1
			else
				0

			val opcode = if(this == BYTE)
				encoding.opcode
			else if(encoding.mask != OpMask.BYTE && BYTE in encoding.mask)
				encoding.opcode + 1
			else
				encoding.opcode

			fun add2(vararg ops: Op) = add(ops.toList(), rexw, o16, opcode)

			when(encoding.ops) {
				Ops.R     -> add2(r())
				Ops.M     -> add2(m())
				Ops.I8    -> add(Op.I8)
				Ops.I16   -> add(Op.I16)
				Ops.I32   -> add(Op.I32)
				Ops.AX    -> add(Op.AX)
				Ops.REL8  -> add(Op.REL8)
				Ops.REL32 -> add(Op.REL32)
				Ops.ST    -> add(Op.ST)
				Ops.FS    -> add(Op.FS)
				Ops.GS    -> add(Op.GS)
				Ops.O     -> add2(r)

				Ops.R_R    -> add2(r, r)
				Ops.R_M    -> add2(r, m)
				Ops.M_R    -> add2(m, r)
				Ops.A_I    -> add2(a, i)
				Ops.RM_I   -> { add2(r, i); add2(m, i) }
				Ops.RM_I8  -> { add2(r, Op.I8); add2(m, Op.I8) }
				Ops.RM_1   -> { add2(r, Op.ONE); add2(m, Op.ONE) }
				Ops.RM_CL  -> { add2(r, Op.CL); add2(m, Op.CL) }
				Ops.ST_ST0 -> add2(Op.ST, Op.ST0)
				Ops.ST0_ST -> add2(Op.ST0, Op.ST)
				Ops.A_O    -> add2(a, r)

				// 3 operands
				Ops.R_RM_I -> { add2(r, r, i); add2(r, m, i) }
				Ops.R_R_I8 -> add(r, r, Op.I8)
				Ops.R_M_I8 -> add(r, m, Op.I8)
				Ops.M_R_I8 -> add(m, r, Op.I8)
				Ops.RM_R_CL -> { add2(r, r, Op.CL); add2(m, r, Op.CL) }

				Ops.MM_MM,
				Ops.MM_I8,
				Ops.MM_MM_I8,
				Ops.MM_M_I8,
				Ops.R_MM_I8,
				Ops.R_MM,
				Ops.MM_R,
				Ops.MM_RM_I8,
				Ops.X_MM,
				Ops.MM_X,
				Ops.X_X,
				Ops.X_I8,
				Ops.X_R_I8,
				Ops.X_M_I8,
				Ops.X_R,
				Ops.M_X_I8,
				Ops.R_X_I8,
				Ops.R_X,
				Ops.X_X_I8,

				Ops.R_RM8,
				Ops.R_RM16,
				Ops.R_RM32,
				Ops.R_REG,
				Ops.R_MEM,
				Ops.R32_RM,
				Ops.R64_M128,
				Ops.I16_I8,
				Ops.O_I,
				Ops.R_SEG,
				Ops.M_SEG,
				Ops.SEG_R,
				Ops.SEG_M,
				Ops.A_MOFFS,
				Ops.MOFFS_A,
				Ops.R_DR,
				Ops.DR_R,
				Ops.R_CR,
				Ops.CR_R,
				Ops.A_I8,
				Ops.I8_A,
				Ops.A_DX,
				Ops.DX_A -> { }

				Ops.X_M,
				Ops.M_X,
				Ops.MM_M,
				Ops.M_MM,
				Ops.NONE,
				Ops.RA,
				Ops.RA_M512 -> { }
			}
		}
	}


	/*fun convert(encoding: Encoding, list: ArrayList<CommonEncoding>) {
		val opsString = encoding.ops.name
		val ops = ArrayList(opsString.split('_'))

		fun add(ops: List<Op>, rexw: Int, o16: Int, opcode: Int) {
			list.add(CommonEncoding(
				encoding.mnemonic.name,
				encoding.prefix,
				encoding.escape,
				opcode,
				encoding.ext,
				ops,
				rexw,
				o16,
				encoding.pseudo
			))
		}

		fun add(ops: List<Op>) = add(ops, encoding.rexw, encoding.o16, encoding.opcode)

		fun add(vararg ops: Op) = add(ops.toList())
		
		var multi: Pair<String, String>? = null
		var multiIndex = -1
		
		for((i, op) in ops.withIndex()) {
			if(op in multiOpMap) {
				multi = multiOpMap[op]!!
				multiIndex = i
			}
		}

		if(encoding.mask.isEmpty) {
			when(opsString) {
				"NONE" -> { add(); return }
				"X_M"  -> { add(Op.X, Op.M128); return }
				"M_X"  -> { add(Op.M128, Op.X); return }
				"MM_M" -> { add(Op.MM, Op.M64); return }
				"M_MM" -> { add(Op.M64, Op.MM); return }
				else   -> error(opsString)
			}
		}

		//if(encoding.mask.isEmpty) { add(ops.map { convert(it, null) }); return }

		encoding.mask.forEachWidth { width ->
			val rexw = if(encoding.rexw == 1)
				1
			else if(width == Width.QWORD && encoding.norexw == 0 && Width.DWORD in encoding.mask && "RA" !in opsString)
				1
			else
				0

			val o16 = if(encoding.o16 == 1)
				1
			else if(width == Width.WORD && encoding.mask != OpMask.WORD)
				1
			else
				0

			val opcode = if(width == Width.BYTE)
				encoding.opcode
			else if(encoding.mask != OpMask.BYTE && Width.BYTE in encoding.mask)
				encoding.opcode + 1
			else
				encoding.opcode

			add(ops.map { convert(it, width) }, rexw, o16, opcode)
		}
	}*/


}