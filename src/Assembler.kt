package eyre

import eyre.gen.EncGen
import eyre.gen.NasmEnc

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
						is Ins       -> assembleIns(node)
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
		if(node is MemNode) {
			mem.node = node.node
			mem.width = node.width
		} else if(node is ImmNode) {
			mem.node = node.node
			mem.width = node.width
		} else {
			mem.node = node
			mem.width = null
		}

		mem.disp = resolveRec(mem.node, mem, true)

		if(node is MemNode) {
			mem.postResolve()
		} else {
			if(mem.hasBase || mem.hasIndex)
				err("Immediate operand cannot have registers")

		}

		return mem
	}



	private fun Any.rel(mem: Mem, width: Width) {
		if(mem.width != null && mem.width != width)
			err("Width mismatch")
		if(mem.relocs != 0) addLinkReloc(width, mem.node, 0, true)
		writer.writeWidth(width, mem.disp)
	}



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

	private fun Mem.write(reg: Int, immLength: Int) {
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
	Assembly
	 */



	private fun assembleIns(ins: Ins) {
		ins.pos = Pos(context.textSec, writer.pos)

		if(ins.count == 0) {
			val opcode = EncGen.zeroOperandOpcodes[ins.mnemonic.ordinal]
			if(opcode == 0) insErr()
			writer.varLengthInt(opcode)
		} else {
			val group = EncGen.manualGroups[ins.mnemonic.name] ?: insErr()
			for(enc in group.encs) {
				if(enc.isCompact) {

				} else {

				}
			}
		}

		ins.size = writer.pos - ins.pos.disp
	}



	/*private fun getAutoEnc(mnemonic: Mnemonic, ops: AutoOps): AutoEnc {
		val encs = Encs.autoEncs[mnemonic] ?: return AutoEnc()
		for(e in encs) if(AutoEnc(e).ops == ops) return AutoEnc(e)
		if(ops.width != 0) return AutoEnc()
		for(e in encs) if(AutoEnc(e).ops.equalsExceptWidth(ops)) return AutoEnc(e)
		return AutoEnc()
	}



	private fun assembleCompact(ins: Ins) {
		var mem = Mem.NULL
		var imm = Mem.NULL

		fun type(node: OpNode?) = when(node) {
			null       -> OpType.NONE
			is RegNode -> node.value.type
			is MemNode -> OpType.MEM.also { mem = resolve(node) }
			is ImmNode -> OpType.IMM.also { imm = resolve(node) }
		}

		val type1 = type(ins.op1)
		val type2 = type(ins.op2)
		val type3 = type(ins.op3)
		val type4 = type(ins.op4)

		when(ins.count) {
			1 -> {

			}
			2 -> {

			}
			3 -> {

			}
		}
	}



	private fun assembleAuto(ins: Ins) {
		var mem = Mem.NULL
		var imm = Mem.NULL

		fun type(node: OpNode?) = when(node) {
			null       -> OpType.NONE
			is RegNode -> node.value.type
			is MemNode -> OpType.MEM.also { mem = resolve(node) }
			is ImmNode -> OpType.IMM.also { imm = resolve(node) }
		}

		val ops = AutoOps(
			type(ins.op1).ordinal,
			type(ins.op2).ordinal,
			type(ins.op3).ordinal,
			type(ins.op4).ordinal,
			mem.width?.let { it.ordinal + 1 } ?: 0,
			mem.vsib
		)

		val enc = getAutoEnc(ins.mnemonic, ops)

		if(enc.isNull) insErr()

		var r: Reg
		val m: Reg
		val v: Reg

		val r1 = if(ins.op1 is RegNode) ins.op1.value else Reg.NONE
		val r2 = if(ins.op2 is RegNode) ins.op2.value else Reg.NONE
		val r3 = if(ins.op3 is RegNode) ins.op3.value else Reg.NONE
		val r4 = if(ins.op4 is RegNode) ins.op4.value else Reg.NONE

		when(OpEnc.entries[enc.opEnc]) {
			OpEnc.RMV -> { r = r1; m = r2; v = r3 }
			OpEnc.RVM -> { r = r1; v = r2; m = r3 }
			OpEnc.MRV -> { m = r1; r = r2; v = r3 }
			OpEnc.MVR -> { m = r1; v = r2; r = r3 }
			OpEnc.VMR -> { v = r1; m = r2; r = r3 }
		}

		if(enc.ext != 0)
			r = Reg.r32(enc.ext)

		if(enc.o16 == 1) byte(0x66)
		if(enc.a32 == 1) byte(0x67)
		if(mem != Mem.NULL) {
			if(mem.a32) byte(0x67)
			if(enc.prefix != 0) byte(enc.prefix)
			writeRex(enc.rw, r.rex, mem.rexX, mem.rexB, r.rex8 or m.rex8, r.noRex or m.noRex)
			enc.writeSimdOpcode()
			mem.write(r.value, 0)
		} else {
			if(enc.prefix != 0) byte(enc.prefix)
			writeRex(enc.rw, r.rex, 0, m.rex, r.rex8 or m.rex8, r.noRex or m.noRex)
			enc.writeSimdOpcode()
			byte(0b11_000_000 or (r.value shl 3) or (m.value))
		}
	}



	private fun AutoEnc.writeSimdOpcode() { when(Escape.entries[escape]) {
		Escape.NONE -> byte(opcode)
		Escape.E0F  -> word(0x0F or (opcode shl 8))
		Escape.E38  -> i24(0x380F or (opcode shl 16))
		Escape.E3A  -> i24(0x3A0F or (opcode shl 16))
	} }*/


}