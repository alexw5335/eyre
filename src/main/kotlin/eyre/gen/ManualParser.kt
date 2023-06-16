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

	private var sse = false


	val encodings = ArrayList<Encoding>()

	val groups = HashMap<Mnemonic, EncodingGroup>()

	val commonEncodings = ArrayList<CommonEncoding>()



	fun read() {
		for(i in inputs.indices) {
			try {
				readLine(inputs[i], encodings)
			} catch(e: Exception) {
				System.err.println("Error on line ${i + 1}: ${inputs[i]}")
				throw e
			}
		}

		for(e in encodings)
			groups.getOrPut(e.mnemonic) { EncodingGroup(e.mnemonic) }.add(e)

		for(g in groups.values)
			g.encodings.sortBy { it.ops.ordinal }

		for(e in encodings)
			convert(e, commonEncodings)
	}



	private fun readLine(input: String, list: ArrayList<Encoding>) {
		if(input.isEmpty() || input.startsWith(';')) return

		val parts = input.split(' ').filter { it.isNotEmpty() }

		var pos = 0

		var prefix = Prefix.NONE
		var escape = Escape.NONE
		var opcode = 0
		var ext = -1
		var mask = OpMask(0)
		var opsString = "NONE"
		var rexw = 0
		var o16 = 0
		val mnemonic: String
		var pseudo = -1
		var sseOps = SseOps.NULL
		var mr = false

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

		if(prefix == Prefix.P9B && opcode == 0) {
			opcode = 0x9B
			prefix = Prefix.NONE
		}

		if(pos < parts.size && parts[pos] != "RW" && parts[pos] != "O16")
			opsString = parts[pos++]

		while(pos < parts.size) {
			when(val part = parts[pos++]) {
				"MR"   -> mr = true
				"RW"   -> rexw = 1
				"O16"  -> o16 = 1
				else   -> if(part.startsWith(":"))
					pseudo = part.drop(1).toInt()
				else
					mask = OpMask(part.toIntOrNull(2) ?: error("Invalid part: $part"))
			}
		}

		fun add(mnemonic: String, opcode: Int, ops: Ops, prefix: Prefix) = list.add(Encoding(
			mnemonicMap[mnemonic] ?: error("Unrecognised mnemonic: $mnemonic"),
			prefix,
			escape,
			opcode,
			mask,
			ext,
			ops,
			sseOps,
			rexw,
			o16,
			pseudo,
			mr
		))

		if(mnemonic == "ADDPD")
			sse = true

		if(mnemonic == "CMPSD" || mnemonic == "MOVSD")
			sseOps = SseOps(SseOp.NONE, SseOp.NONE, SseOp.NONE)

		if(sse) {
			val ops = opsString.split('_').map { when(it) {
				"X"    -> SseOp.X
				"MM"   -> SseOp.MM
				"I8"   -> SseOp.I8
				"R8"   -> SseOp.R8
				"R16"  -> SseOp.R16
				"R32"  -> SseOp.R32
				"R64"  -> SseOp.R64
				"MEM"  -> { mask = OpMask.NONE; SseOp.M }
				"M8"   -> { mask = OpMask.BYTE; SseOp.M }
				"M16"  -> { mask = OpMask.WORD; SseOp.M }
				"M32"  -> { mask = OpMask.DWORD; SseOp.M }
				"M64"  -> { mask = OpMask.QWORD; SseOp.M }
				"M128" -> { mask = OpMask.XWORD; SseOp.M }
				else   -> error("Invalid SSE operand: $it")
			} }

			sseOps = when(ops.size) {
				1 -> SseOps(ops[0], SseOp.NONE, SseOp.NONE)
				2 -> SseOps(ops[0], ops[1], SseOp.NONE)
				3 -> SseOps(ops[0], ops[1], ops[2])
				else -> error("Invalid sse ops")
			}
			add(mnemonic, opcode, Ops.NONE, prefix)
			return
		}

		val multi = multiMap[opsString]
		val ops = opsMap[opsString]

		if(multi?.mask != null)
			if(mask.isNotEmpty)
				error("Mask already present")
			else
				mask = multi.mask

		if(multi == null && ops == null)
			error("Unrecognised ops: $opsString")

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
				encoding.pseudo,
				encoding.mr
			))
		}

		fun add(ops: List<Op>) = add(ops, encoding.rexw, encoding.o16, encoding.opcode)

		fun add(vararg ops: Op) = add(ops.toList())

		fun convert(op: SseOp) = when(op) {
			SseOp.NONE -> Op.NONE
			SseOp.X -> Op.X
			SseOp.MM -> Op.MM
			SseOp.I8 -> Op.I8
			SseOp.R8 -> Op.R8
			SseOp.R16 -> Op.R16
			SseOp.R32 -> Op.R32
			SseOp.R64 -> Op.R64
			SseOp.M -> when(encoding.mask) {
				OpMask.BYTE  -> Op.M8
				OpMask.WORD  -> Op.M16
				OpMask.DWORD -> Op.M32
				OpMask.QWORD -> Op.M64
				OpMask.XWORD -> Op.M128
				OpMask.NONE  -> Op.MEM
				else -> error("Invalid mask: $encoding")
			}
		}

		if(encoding.sseOps != SseOps.NULL) {
			val op1 = convert(encoding.sseOps.op1)
			val op2 = convert(encoding.sseOps.op2)
			val op3 = convert(encoding.sseOps.op3)
			add(if(op3 == Op.NONE) listOf(op1, op2) else listOf(op1, op2, op3))
			return
		}

		if(encoding.mask.isEmpty) {
			when(encoding.ops) {
				Ops.NONE     -> add()
				Ops.I16_I8   -> add(Op.I16, Op.I8)
				Ops.I8       -> add(Op.I8)
				Ops.I16      -> add(Op.I16)
				Ops.I32      -> add(Op.I32)
				Ops.AX       -> add(Op.AX)
				Ops.REL8     -> add(Op.REL8)
				Ops.REL32    -> add(Op.REL32)
				Ops.ST       -> add(Op.ST)
				Ops.FS       -> add(Op.FS)
				Ops.GS       -> add(Op.GS)
				Ops.ST_ST0   -> add(Op.ST, Op.ST0)
				Ops.ST0_ST   -> add(Op.ST0, Op.ST)
				Ops.M        -> add(Op.MEM)
				else         -> error("Invalid: $encoding")
			}
			return
		}

		encoding.mask.forEachWidth { with(it) {
			val rexw = if(encoding.rexw == 1)
				1
			else if(this == QWORD && DWORD in encoding.mask && encoding.ops != Ops.RA_M512 && encoding.ops != Ops.RA)
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
				Ops.R     -> add2(r)
				Ops.M     -> add2(m)
				Ops.O     -> add2(r)

				Ops.R_R    -> add2(r, r)
				Ops.R_M    -> add2(r, m)
				Ops.M_R    -> add2(m, r)
				Ops.A_I    -> add2(a, i)
				Ops.RM_I   -> { add2(r, i); add2(m, i) }
				Ops.RM_I8  -> { add2(r, Op.I8); add2(m, Op.I8) }
				Ops.RM_1   -> { add2(r, Op.ONE); add2(m, Op.ONE) }
				Ops.RM_CL  -> { add2(r, Op.CL); add2(m, Op.CL) }
				Ops.A_O    -> add2(a, r)

				Ops.R_RM_I  -> { add2(r, r, i); add2(r, m, i) }
				Ops.R_RM_I8 -> { add2(r, r, Op.I8); add2(r, m, Op.I8) }
				Ops.RM_R_I8 -> { add2(r, r, Op.I8); add2(m, r, Op.I8) }
				Ops.RM_R_CL -> { add2(r, r, Op.CL); add2(m, r, Op.CL) }

				Ops.R_MEM   -> add2(r, Op.MEM)
				Ops.R_RM8   -> { add2(r, Op.R8); add2(r, Op.M8) }
				Ops.R_RM16  -> { add2(r, Op.R16); add2(r, Op.M16) }
				Ops.R_RM32  -> { add2(r, Op.R32); add2(r, Op.M32) }
				Ops.RA      -> add2(r)
				Ops.RA_M512 -> add2(r, Op.M512)
				Ops.R_M128  -> add(r, Op.M128)

				else -> return
			}
		}}
	}


}