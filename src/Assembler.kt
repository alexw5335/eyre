package eyre

import eyre.gen.EncGen
import eyre.gen.ManualEnc
import eyre.gen.ManualGroup

class Assembler(private val context: Context) {


	private var writer = context.textWriter

	private var section = context.textSec



	fun assemble() {
		for(file in context.srcFiles) {
			for(i in file.nodes.indices) {
				val node = file.nodes[i]
				val start = writer.pos

				try {
					when(node) {
						is InsNode       -> assembleIns(node)
						//is Label     -> handleLabel(node)
						//is Proc      -> handleProc(node)
						//is Directive -> handleDirective(node, index, srcFile.nodes)
						//is ScopeEnd  -> handleScopeEnd(node, index, srcFile.nodes)
						//is Var       -> handleVar(node)
						else         -> Unit
					}
				} catch(e: EyreError) {
					if(e.srcPos.isNull) e.srcPos = node.srcPos
					writer.pos = start
					file.invalid = true
					break
				}
			}
		}
	}



	fun stringLiteral(string: String): PosSym {
		context.dataWriter.align(8)
		val pos = context.dataWriter.pos
		for(c in string)
			context.dataWriter.i8(c.code)
		context.dataWriter.i32(0)
		return AnonPosSym(Pos(context.dataSec, pos))
	}



	/*
	Errors
	 */



	private fun err(srcPos: SrcPos, message: String): Nothing =
		context.err(srcPos, message)

	private fun err(message: String): Nothing =
		context.err(SrcPos(), message)

	private fun insErr(message: String = "Invalid encoding"): Nothing =
		context.err(SrcPos(), message)



	/*
	Nodes
	 */




	/*
	Resolution
	 */



	private fun addLinkReloc(width: Width, node: Node, offset: Int, rel: Boolean) =
		context.linkRelocs.add(Reloc(Pos(section, writer.pos), node, width, offset, rel))

	private fun addAbsReloc(node: Node) =
		context.absRelocs.add(Reloc(Pos(section, writer.pos), node, Width.QWORD, 0, false))



	private fun resolveRec(node: Node, mem: Mem, regValid: Boolean): Long {
		fun addReloc() {
			if(mem.relocs++ == 0 && !regValid)
				err("First relocation (absolute or relative) must be positive and absolute")
		}

		if(node is IntNode) return node.value
		if(node is UnNode) return node.calc(regValid) { n, v -> resolveRec(n, mem, v) }
		if(node is StringNode) {
			val symbol = stringLiteral(node.value)
			node.sym = symbol
			addReloc()
			return 0L
		}

		if(node is BinNode) {
			if(node.op == BinOp.MUL) {
				val regNode = node.left as? RegNode ?: node.right as? RegNode
				val scaleNode = node.left as? IntNode ?: node.right as? IntNode

				if(regNode != null && scaleNode != null) {
					if(mem.hasIndex && !regValid)
						err("Too many registers in memory operand")
					mem.checkReg(regNode.value)
					mem.index = regNode.value
					mem.scale = scaleNode.value.toInt()
					return 0
				}
			}

			return node.calc(regValid) { n, v -> resolveRec(n, mem, v) }
		}

		if(node is RegNode) {
			if(!regValid)
				err("Register not valid here")
			mem.checkReg(node.value)
			if(mem.hasBase) {
				if(mem.hasIndex)
					err("Too many registers in memory operand")
				mem.index = node.value
				mem.scale = 1
			} else {
				mem.base = node.value
			}
			return 0
		}

		error("Invalid imm node: $node")
	}



	private fun Mem.postResolve() {
		if(vsib != 0) {
			if(!index.isV) {
				if(hasIndex) {
					if(scale != 1)
						err("Index register must be SIMD")
					swapRegs()
				} else {
					index = base
					base = Reg.NONE
				}
			}
			if(scale.countOneBits() > 1 || scale > 8)
				err("Invalid memory operand scale")
			return
		}

		// Index cannot be ESP/RSP, swap to base if possible
		if(hasIndex && index.isInvalidIndex) {
			when {
				scale != 1 -> err("Index cannot be ESP/RSP")
				hasBase    -> swapRegs()
				else       -> { base = index; index = Reg.NONE }
			}
		} else if(hasIndex && base.value == 5 && scale == 1 && index.value != 5) {
			swapRegs()
		}

		fun scaleErr(): Nothing = err("Invalid memory operand scale")

		// 1: [R*1] -> [R], avoid SIB
		// 2: [R*2] -> [R+R*1], avoid index-only SIB which produces DISP32 of zero
		// 3: [R*3] -> [R+R*2], [R+R*3] -> invalid
		// 5: [R*5] -> [R+R*4], [R+R*5] -> invalid
		when(scale) {
			0 -> index = Reg.NONE
			1 -> if(!hasBase) { base = index; index = Reg.NONE }
			2 -> if(!hasBase) { scale = 1; base = index }
			3 -> if(!hasBase) { scale = 2; base = index } else scaleErr()
			4 -> { }
			5 -> if(!hasBase) { scale = 4; base = index } else scaleErr()
			6 -> scaleErr()
			7 -> scaleErr()
			8 -> { }
			else -> scaleErr()
		}
	}



	private fun Mem.swapRegs() {
		val temp = index
		index = base
		base = temp
	}



	private fun Mem.checkReg(reg: Reg) {
		when(reg.type) {
			OpType.R32 -> if(aso == 2) err("Invalid base/index register") else aso = 1
			OpType.R64 -> if(aso == 1) err("Invalid base/index register") else aso = 2
			OpType.X   -> vsib = 1
			OpType.Y   -> vsib = 2
			OpType.Z   -> vsib = 3
			else       -> err("Invalid base/index register")
		}
	}



	private fun resolve(node: Node): Mem {
		val mem = Mem()

		if(node is OpNode) {
			mem.node = node.node
			mem.width = node.width
		} else {
			mem.node = node
			mem.width = null
		}

		mem.disp = resolveRec(mem.node, mem, true)

		if(node is OpNode && node.type == OpType.MEM) {
			mem.postResolve()
		} else {
			if(mem.hasBase || mem.hasIndex)
				err("Immediate operand cannot have registers")
		}

		return mem
	}



	@Suppress("UnusedReceiverParameter")
	private fun Any.rel(mem: Mem, width: Width) {
		if(mem.width != null && mem.width != width)
			err("Width mismatch")
		if(mem.relocs != 0) addLinkReloc(width, mem.node, 0, true)
		writer.writeWidth(width, mem.disp)
	}



	@Suppress("UnusedReceiverParameter")
	private fun Any.imm(mem: Mem, width: Width) {
		if(mem.width != null && mem.width != width)
			err("Width mismatch")
		if(mem.relocs == 1) {
			if(width != Width.QWORD)
				err("Absolute relocation must be 64-bit")
			addAbsReloc(mem.node)
			writer.advance(8)
		} else if(mem.relocs > 1) {
			addLinkReloc(width, mem.node, 0, false)
			writer.advance(width.bytes)
		} else if(!writer.writeWidth(width, mem.disp)) {
			err("Immediate value is out of range (${mem.disp})")
		}
	}



	private fun Any.rel(op: OpNode, width: Width) =
		rel(resolve(op), width)



	private fun Any.imm(op: OpNode, width: Width) =
		imm(resolve(op), width)



	private fun resolveImmSimple(n: Node): Long = when(n) {
		is IntNode -> n.value
		is UnNode  -> n.calc(::resolveImmSimple)
		is BinNode -> n.calc(::resolveImmSimple)
		else       -> err("Invalid immediate node")
	}



	/*
	Encoding
	 */



	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun i24(value: Int) = writer.i24(value)

	private fun dword(value: Int) = writer.i32(value)

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int) {
		val value = 0b0100_0000 or (w shl 3) or (r shl 2) or (x shl 1) or b
		if(value != 0b0100_0000) byte(value)
	}

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int, forced: Int, banned: Int) {
		val value = (w shl 3) or (r shl 2) or (x shl 1) or b
		if(forced == 1 || value != 0)
			if(banned == 1)
				insErr("REX prefix not allowed here")
			else
				byte(0b0100_0000 or value)
	}

	/**
	 *     wvvv-vlpp_rxbm-mmmm_1100-0100
	 *     r: ~REX.R (ModRM:REG)
	 *     x: ~REX.X (SIB:INDEX)
	 *     b: ~REX.B (SIB:BASE, MODRM:RM, OPREG)
	 */
	private fun ManualEnc.writeVex(r: Int, x: Int, b: Int, vvvv: Int) {
		if(vexw.value != 0 || escape.avxValue > 1 || x == 0 || b == 0)
			dword(
				(0xC4 shl 0) or
					(r shl 15) or (x shl 14) or (b shl 13) or (escape.avxValue shl 8) or
					(vexw.value shl 23) or (vvvv shl 19) or (vexl.value shl 18) or (prefix.avxValue shl 16) or
					(opcode shl 24)
			)
		else
			i24(
				(0xC5 shl 0) or
					(r shl 15) or (vvvv shl 11) or (vexl.value shl 10) or (prefix.avxValue shl 8) or
					(opcode shl 16)
			)
	}

	private fun checkWidth(mask: Int, width: Int) {
		if((1 shl width) and mask == 0)
			insErr("Invalid operand width")
	}

	private fun writeO16(mask: Int, width: Int) {
		if(mask != 0b10 && width == 1) writer.i8(0x66)
	}

	private fun writeA32(mem: Mem) {
		if(mem.vsib != 0) insErr("VSIB not valid here")
		if(mem.aso == 1) byte(0x67)
	}

	/** Return 1 if width is QWORD (3) and widths has DWORD (2) set, otherwise 0 */
	private fun rexw(mask: Int, width: Int) = ((1 shl width) shr 3) and (mask shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun writeOpcode(opcode: Int, mask: Int, width: Int) {
		val addition = (mask and 1) and (1 shl width).inv()
		if(opcode and 0xFF00 != 0) word(opcode + (addition shl 8)) else byte(opcode + addition)
	}

	private fun Mem.writeMem(reg: Int, immLength: Int) {
		fun reloc(mod: Int) {
			when {
				hasReloc -> { addLinkReloc(Width.DWORD, node, 0, false); writer.i32(0) }
				mod == 1 -> writer.i8(disp.toInt())
				mod == 2 -> writer.i32(disp.toInt())
			}
		}

		val mod = when {
			hasReloc    -> 2 // disp32, can't be sure of size
			disp == 0L  -> 0 // no disp
			disp.isImm8 -> 1 // disp8
			else        -> 2 // disp32
		}

		val s = scale.countTrailingZeroBits()
		val i = index.value
		val b = base.value

		if(hasIndex) { // SIB: [R*S] or [R*S+DISP] or [R+R*S] or [R+R*S+DISP]
			if(hasBase) {
				if(b == 5 && mod == 0) { // [RBP/R13+R*S] -> [RBP+R*S+DISP8]
					i24(0b01_000_100 or (reg shl 3) or (s shl 14) or (i shl 11) or (0b101 shl 8))
				} else {
					word((mod shl 6) or (reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (b shl 8))
					reloc(mod)
				}
			} else { // Index only, requires disp32
				word((reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (0b101 shl 8))
				reloc(0b10)
			}
		} else if(hasBase) { // Indirect: [R] or [R+DISP]
			if(b == 4) { // [RSP/R12] -> [RSP/R12+NONE*1] (same with DISP)
				word((mod shl 6) or (reg shl 3) or 0b100 or (0b00_100_100 shl 8))
				reloc(mod)
			} else if(b == 5 && mod == 0) { // [RBP/R13] -> [RBP/R13+0]
				word(0b00000000_01_000_101 or (reg shl 3))
			} else {
				byte((mod shl 6) or (reg shl 3) or b)
				reloc(mod)
			}
		} else if(relocs and 1 == 1) { // RIP-relative (odd reloc count)
			byte((reg shl 3) or 0b101)
			addLinkReloc(Width.DWORD, node, immLength, true)
			dword(0)
		} else { // Absolute 32-bit or empty memory operand
			word(0b00_100_101_00_000_100 or (reg shl 3))
			reloc(0b10)
		}
	}



	/*
	Encoding
	 */



	private fun writePrefix(enc: ManualEnc) {
		when(enc.prefix) {
			Prefix.NONE -> Unit
			Prefix.P66  -> writer.i8(0x66)
			Prefix.PF2  -> writer.i8(0xF2)
			Prefix.PF3  -> writer.i8(0xF3)
			Prefix.P9B  -> writer.i8(0x9B)
		}
	}

	private fun writeEscape(enc: ManualEnc) {
		when(enc.escape) {
			Escape.NONE -> Unit
			Escape.E0F  -> writer.i8(0x0F)
			Escape.E38  -> writer.i16(0x380F)
			Escape.E3A  -> writer.i16(0x3A0F)
		}
	}

	private fun writeOpcode(enc: ManualEnc) {
		writeEscape(enc)
		if(enc.opcode and 0xFF00 != 0) word(enc.opcode) else byte(enc.opcode)
	}

	private fun encodeNone(enc: ManualEnc) {
		if(enc.o16 == 1) writer.i8(0x66)
		writePrefix(enc)
		if(enc.rw == 1) writer.i8(0x48)
		writeEscape(enc)
		if(enc.opcode and 0xFF00 != 0) word(enc.opcode) else byte(enc.opcode)
	}

	private fun encode1R(enc: ManualEnc, op1: Reg) {
		val width = op1.width.ordinal
		val mask = enc.mask
		if((1 shl width) and mask == 0) insErr()
		if(enc.mask != 2 && width == 1) writer.i8(0x66)
		if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
		writeRex(((1 shl width) shr 3) and (mask shr 2), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeEscape(enc)
		writeOpcode(enc.opcode, mask, width)
		byte(0b11_000_000 or (enc.actualExt shl 3) or op1.value)
	}

	private fun encode1O(enc: ManualEnc, op1: Reg) {
		val width = op1.width.ordinal
		val mask = enc.mask
		if((1 shl width) and mask == 0) insErr()
		if(enc.mask != 2 && width == 1) writer.i8(0x66)
		writeRex(((1 shl width) shr 3) and (mask shr 2), 0, 0, op1.rex, op1.rex8, op1.noRex)
		byte(enc.opcode + (((mask and 1) and (1 shl width).inv()) shl 3) + op1.value)
	}

	private fun encode1M(enc: ManualEnc, op1: OpNode, immLength: Int) {
		val mem = resolve(op1)
		val width = op1.width?.ordinal ?: insErr()
		val mask = enc.mask
		if((1 shl width) and mask == 0) insErr()
		if(enc.mask != 2 && width == 1) writer.i8(0x66)
		if(mem.vsib != 0) insErr("VSIB not valid here")
		if(mem.aso == 1) byte(0x67)
		writeRex(((1 shl width) shr 3) and (mask shr 2), 0, mem.rexX, mem.rexB)
		writeOpcode(enc.opcode, mask, width)
		mem.writeMem(enc.actualExt, immLength)
	}



	/*
	Assembly
	 */



	private val Ops.enc get() = if(this !in group) insErr() else group[this]

	private lateinit var group: ManualGroup



	private fun assembleIns(ins: InsNode) {
		ins.pos = Pos(context.textSec, writer.pos)

		if(ins.count == 0) {
			val opcode = EncGen.zeroOperandOpcodes[ins.mnemonic.ordinal]
			if(opcode == 0) insErr()
			writer.varLengthInt(opcode)
		} else {
			group = EncGen.manualGroups[ins.mnemonic] ?: error("Missing mneomnic")
			if(group.isCompact)
				assembleCompact(ins)
			else
				assembleAuto(ins)
		}

		ins.size = writer.pos - ins.pos.disp
	}



	private fun assembleCompact(ins: InsNode) {
		when(ins.count) {
			1 -> when(ins.op1.type) {
				OpType.MEM -> encode1M(Ops.M.enc, ins.op1, 0)
				OpType.IMM -> assemble1I(ins.op1)
				else       -> assemble1R(ins.op1.reg)
			}
			2 -> { }
			3 -> { }
			else -> insErr()
		}
	}



	private fun assemble1R(op1: Reg) {
		when {
			op1.isR -> when {
				Ops.R in group -> {
					val enc = Ops.R.enc
					if(enc.opreg)
						encode1O(enc, op1)
					else
						encode1R(Ops.R.enc, op1)
				}
				op1 == Reg.AX   -> encodeNone(Ops.AX.enc)
				else            -> insErr()
			}
			op1 == Reg.FS -> encodeNone(Ops.FS.enc)
			op1 == Reg.GS -> encodeNone(Ops.GS.enc)
			else          -> insErr()
		}
	}



	private fun assemble1I(op1: OpNode) {
		val imm = resolve(op1)

		fun imm(ops: Ops, width: Width) {
			encodeNone(ops.enc)
			imm(imm, width)
		}

		fun rel(ops: Ops, width: Width) {
			encodeNone(ops.enc)
			rel(imm, width)
		}

		when {
			group.mnemonic == Mnemonic.PUSH -> when {
				op1.width == Width.BYTE  -> imm(Ops.I8, Width.BYTE)
				op1.width == Width.WORD  -> imm(Ops.I16, Width.WORD)
				op1.width == Width.DWORD -> imm(Ops.I32, Width.DWORD)
				imm.hasReloc -> imm(Ops.I32, Width.DWORD)
				imm.isImm8   -> imm(Ops.I8, Width.BYTE)
				imm.isImm16  -> imm(Ops.I16, Width.WORD)
				else         -> imm(Ops.I32, Width.DWORD)
			}

			Ops.REL32 in group ->
				if(Ops.REL8 in group && ((!imm.hasReloc && imm.isImm8) || op1.width == Width.BYTE))
					rel(Ops.REL8, Width.BYTE)
				else
					rel(Ops.REL32, Width.DWORD)

			Ops.REL8 in group -> rel(Ops.REL8, Width.BYTE)
			Ops.I8   in group -> imm(Ops.I8, Width.BYTE)
			Ops.I16  in group -> imm(Ops.I16, Width.WORD)
			else              -> insErr()
		}
	}



	private fun getAutoEnc(ops: AutoOps): ManualEnc? {
		for(e in group.encs)
			if(e.autoOps == ops)
				return e

		if(ops.width != 0)
			return null

		for(e in group.encs)
			if(e.autoOps.equalsExceptWidth(ops))
				return e

		return null
	}



	private fun assembleAuto(ins: InsNode) {
		var mem = Mem.NULL
		var imm = Mem.NULL
		var immLength = 0

		fun check(node: OpNode) {
			if(node.type == OpType.IMM) {
				if(node.width != null && node.width != Width.BYTE)
					insErr()
				imm = resolve(node)
				if(!imm.isImm8)
					insErr()
				immLength = 1
			} else if(node.type == OpType.MEM) {
				mem = resolve(node)
			}
		}

		check(ins.op1)
		check(ins.op2)
		check(ins.op3)
		check(ins.op4)

		val ops = AutoOps(
			ins.op1.type.ordinal,
			ins.op2.type.ordinal,
			ins.op3.type.ordinal,
			ins.op4.type.ordinal,
			mem.width?.let { it.ordinal + 1 } ?: 0,
			mem.vsib
		)

		val enc = getAutoEnc(ops) ?: insErr()

		var r: Reg
		val m: Reg
		val v: Reg

		val r1 = ins.op1.reg
		val r2 = ins.op2.reg
		val r3 = ins.op3.reg

		when(enc.opEnc) {
			OpEnc.RMV -> { r = r1; m = r2; v = r3 }
			OpEnc.RVM -> { r = r1; v = r2; m = r3 }
			OpEnc.MRV -> { m = r1; r = r2; v = r3 }
			OpEnc.MVR -> { m = r1; v = r2; r = r3 }
			OpEnc.VMR -> { v = r1; m = r2; r = r3 }
		}

		if(enc.hasExt)
			r = Reg.r32(enc.ext)
		if(enc.o16 == 1)
			byte(0x66)
		if(enc.a32 == 1)
			byte(0x67)

		if(enc.vex) {
			if(mem != Mem.NULL) {
				if(mem.a32) byte(0x67)
				enc.writeVex(r.vexRex, mem.vexX, mem.vexB, v.vValue)
				mem.writeMem(r.value, immLength)
			} else {
				enc.writeVex(r.vexRex, 1, m.vexRex, v.vValue)
				byte(0b11_000_000 or (r.value shl 3) or (m.value))
			}
		} else {
			if(mem != Mem.NULL) {
				if(mem.a32) byte(0x67)
				if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
				writeRex(enc.rw, r.rex, mem.rexX, mem.rexB, r.rex8 or m.rex8, r.noRex or m.noRex)
				enc.writeSimdOpcode()
				mem.writeMem(r.value, immLength)
			} else {
				if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
				writeRex(enc.rw, r.rex, 0, m.rex, r.rex8 or m.rex8, r.noRex or m.noRex)
				enc.writeSimdOpcode()
				byte(0b11_000_000 or (r.value shl 3) or (m.value))
				println(r)
				println(m)
			}
		}

		when {
			ins.op4.reg != Reg.NONE -> byte(ins.op4.reg.index shl 4)
			imm != Mem.NULL -> imm(imm, Width.BYTE)
			enc.pseudo >= 0 -> byte(enc.pseudo)
		}
	}



	private fun ManualEnc.writeSimdOpcode() { when(escape) {
		Escape.NONE -> byte(opcode)
		Escape.E0F  -> word(0x0F or (opcode shl 8))
		Escape.E38  -> i24(0x380F or (opcode shl 16))
		Escape.E3A  -> i24(0x3A0F or (opcode shl 16))
	} }


}