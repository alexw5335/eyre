package eyre

import eyre.gen.EncGen
import eyre.gen.ParsedEnc
import eyre.Width.*

class Assembler(private val context: Context) {


	private var writer = context.textWriter

	private var section = context.textSec

	private var currentIns: InsNode? = null

	private val assemblers = Array<(InsNode) -> Unit>(Mnemonic.entries.size) { ::assembleAuto }



	init {
		populateAssemblers()
	}



	fun assemble() {
		for(file in context.srcFiles) {
			for(i in file.nodes.indices) {
				val node = file.nodes[i]
				val start = writer.pos

				try {
					when(node) {
						is InsNode     -> assembleIns(node)
						is Label       -> handleLabel(node)
						is Proc        -> handleProc(node)
						//is Directive -> handleDirective(node, index, srcFile.nodes)
						is ScopeEnd  -> handleScopeEnd(node)
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
		if(currentIns != null)
			context.err(SrcPos(), "$message  --  ${NodeStrings.string(currentIns!!)}")
		else
			context.err(SrcPos(), message)

	private fun widthMismatchErr(): Nothing =
		insErr("Width mismatch")

	private fun noWidthErr(): Nothing =
		insErr("Width not specified")



	/*
	Nodes
	 */



	private fun handleLabel(symbol: PosSym) {
		symbol.pos = Pos(symbol.pos.sec, writer.pos)
		if(symbol.name == Name.MAIN) {
			if(context.entryPoint != null)
				error("Redeclaration of entry point")
			context.entryPoint = symbol
		}
	}



	private fun handleScopeEnd(node: ScopeEnd) {
		if(node.sym !is Proc)
			return

		node.sym.size = writer.pos - node.sym.pos.disp
	}



	private fun handleProc(node: Proc) {
		handleLabel(node)
	}



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
			mem.width = NONE
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
		if(width == NONE)
			err("Invalid width")
		if(mem.width != NONE && mem.width != width)
			err("Width mismatch")
		if(mem.relocs != 0)
			addLinkReloc(width, mem.node, 0, true)
		writer.writeWidth(width, mem.disp)
	}



	@Suppress("UnusedReceiverParameter")
	private fun Any.imm(mem: Mem, width: Width, imm64: Boolean = false) {
		val actualWidth = if(width == QWORD && !imm64) DWORD else width

		if(width == NONE)
			err("Invalid width")

		if(mem.width != NONE && mem.width != actualWidth)
			err("Width mismatch")

		if(mem.relocs == 1) {
			if(actualWidth != QWORD)
				err("Absolute relocation must be 64-bit")
			addAbsReloc(mem.node)
			writer.advance(8)
		} else if(mem.relocs > 1) {
			addLinkReloc(actualWidth, mem.node, 0, false)
			writer.advance(actualWidth.bytes)
		} else if(!writer.writeWidth(actualWidth, mem.disp)) {
			err("Immediate value is out of range")
		}
	}



	private fun Any.rel(op: OpNode, width: Width) =
		rel(resolve(op), width)



	private fun Any.imm(op: OpNode, width: Width) =
		imm(resolve(op), width)



	private fun resolveSimple(n: Node): Long = when(n) {
		is IntNode -> n.value
		is UnNode  -> n.calc(::resolveSimple)
		is BinNode -> n.calc(::resolveSimple)
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

	private fun Mem.writeMem(reg: Int, immWidth: Width) {
		fun reloc(mod: Int) {
			when {
				hasReloc -> { addLinkReloc(DWORD, node, 0, false); writer.i32(0) }
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
			// immWidth can be QWORD
			byte((reg shl 3) or 0b101)
			addLinkReloc(DWORD, node, immWidth.bytes.coerceAtMost(4), true)
			dword(0)
		} else { // Absolute 32-bit or empty memory operand
			word(0b00_100_101_00_000_100 or (reg shl 3))
			reloc(0b10)
		}
	}



	/*
	Encoding
	 */



	private fun encodeNone(opcode: Int, width: Width) {
		when(width) {
			BYTE  -> byte(opcode)
			WORD  -> word((opcode + 1) shl 8 or 0x66)
			DWORD -> byte(opcode + 1)
			QWORD -> word(((opcode + 1) shl 8) or 0x48)
			else  -> insErr()
		}
	}

	private fun encode1REL(opcode: Int, width: Width, node: InsNode) {
		if(node.op1.type != OpType.IMM) insErr()
		writer.varLengthInt(opcode)
		rel(node.op1, width)
	}

	private fun encode1I(opcode: Int, width: Width, node: InsNode) {
		if(node.op1.type != OpType.IMM) insErr()
		writer.varLengthInt(opcode)
		imm(node.op1, width)
	}

	private fun encode1I(opcode: Int, imm: OpNode, width: Width) {
		when(width) {
			BYTE  -> byte(opcode).imm(imm, BYTE)
			WORD  -> word(((opcode + 1) shl 8) or 0x66).imm(imm, WORD)
			DWORD -> byte(opcode + 1).imm(imm, DWORD)
			QWORD -> word(((opcode + 1) shl 8) or 0x48).imm(imm, DWORD)
			else  -> insErr()
		}
	}

	private fun encode1MSingle(opcode: Int, width: Width, ext: Int, op1: OpNode) {
		val mem = resolve(op1)
		if(op1.width.memNotEqual(width)) widthMismatchErr()
		writeA32(mem)
		writeRex(0, 0, mem.rexX, mem.rexB)
		writer.varLengthInt(opcode)
		mem.writeMem(ext, NONE)
	}

	private fun encode1R(opcode: Int, mask: Int, ext: Int, op1: Reg) {
		val width = op1.width.ordinal - 1
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeOpcode(opcode, mask, width)
		byte(0b11_000_000 or (ext shl 3) or op1.value)
	}

	private fun encode1O(opcode: Int, mask: Int, op1: Reg) {
		val width = op1.width.ordinal - 1
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		byte(opcode + (((mask and 1) and (1 shl width).inv()) shl 3) + op1.value)
	}

	private fun encode1MEM(opcode: Int, ext: Int, op1: OpNode) {
		val mem = resolve(op1)
		writeA32(mem)
		writeRex(0, 0, mem.rexX, mem.rexB)
		writer.varLengthInt(opcode)
		mem.writeMem(ext, NONE)
	}

	private fun encode1M(opcode: Int, mask: Int, ext: Int, op1: OpNode, immWidth: Width) {
		val mem = resolve(op1)
		val width = op1.width.ordinal - 1
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32(mem)
		writeRex(rexw(mask, width), 0, mem.rexX, mem.rexB)
		writeOpcode(opcode, mask, width)
		mem.writeMem(ext, immWidth)
	}

	private fun encode2RM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immWidth: Width) {
		val width = op1.width.ordinal - 1
		if(op2.width.memNotEqual(op1.width)) widthMismatchErr()
		val mem = resolve(op2)
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32(mem)
		writeRex(rexw(mask, width), op1.rex, mem.rexX, mem.rexB, op1.rex8, op1.noRex)
		writeOpcode(opcode, mask, width)
		mem.writeMem(op1.value, immWidth)
	}

	private fun encode2RR(opcode: Int, mask: Int, op1: Reg, op2: Reg) {
		val width = op1.width.ordinal - 1
		if(op1.type != op2.type) widthMismatchErr()
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), op1.rex, 0, op2.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex)
		writeOpcode(opcode, mask, width)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encode1RM(opcode: Int, mask: Int, ext: Int, op1: OpNode, immWidth: Width) =
		if(op1.isMem)
			encode1M(opcode, mask, ext, op1, immWidth)
		else
			encode1R(opcode, mask, ext, op1.reg)

	private fun encode2RRM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immWidth: Width) =
		if(op2.isMem)
			encode2RM(opcode, mask, op1, op2, immWidth)
		else
			encode2RR(opcode, mask, op1, op2.reg)

	private fun encode2RMR(opcode: Int, mask: Int, op1: OpNode, op2: Reg, immWidth: Width) =
		if(op1.isMem)
			encode2RM(opcode, mask, op2, op1, immWidth)
		else
			encode2RR(opcode, mask, op2, op1.reg)



	/*
	Assembly
	 */



	fun assembleIns(ins: InsNode) {
		currentIns = ins
		ins.pos = Pos(context.textSec, writer.pos)

		when {
			ins.count == 0 -> {
				val opcode = EncGen.zeroOperandOpcodes[ins.mnemonic.ordinal]
				if(opcode == 0) insErr()
				writer.varLengthInt(opcode)
			}
			ins.op1.type == OpType.ST -> assembleFpu(ins)
			else -> assemblers[ins.mnemonic.ordinal](ins)
		}

		ins.size = writer.pos - ins.pos.disp
		currentIns = null
	}



	fun insertZero() = byte(0)



	private fun populateAssemblers() {
		operator fun Mnemonic.plusAssign(assembler: (InsNode) -> Unit) = assemblers.set(ordinal, assembler)

		Mnemonic.DLLCALL += ::encodeDLLCALL
		Mnemonic.PUSH    += ::encodePUSH
		Mnemonic.POP     += ::encodePOP
		Mnemonic.IN      += ::encodeIN
		Mnemonic.OUT     += ::encodeOUT
		Mnemonic.MOV     += ::encodeMOV
		Mnemonic.BSWAP   += ::encodeBSWAP
		Mnemonic.XCHG    += ::encodeXCHG
		Mnemonic.TEST    += ::encodeTEST
		Mnemonic.IMUL    += ::encodeIMUL
		Mnemonic.PUSHW   += ::encodePUSHW
		Mnemonic.POPW    += ::encodePOPW
		Mnemonic.CALL    += ::encodeCALL
		Mnemonic.JMP     += ::encodeJMP
		Mnemonic.LEA     += ::encodeLEA
		Mnemonic.ENTER   += ::encodeENTER
		Mnemonic.HRESET  += ::encodeHRESET
		Mnemonic.FSTSW   += ::encodeFSTSW
		Mnemonic.FNSTSW  += ::encodeFSTSW
		Mnemonic.FSAVE   += { encodeFSAVE(0xDD, 6, it) }
		Mnemonic.FSTCW   += { encodeFSAVE(0xD9, 7, it) }
		Mnemonic.FSTENV  += { encodeFSAVE(0xD9, 6, it) }
		Mnemonic.JO      += { encodeJCC(0x70, 0x800F, it) }
		Mnemonic.JNO     += { encodeJCC(0x71, 0x810F, it) }
		Mnemonic.JB      += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JNAE    += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JC      += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JNB     += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JAE     += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JNC     += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JZ      += { encodeJCC(0x74, 0x840F, it) }
		Mnemonic.JE      += { encodeJCC(0x74, 0x840F, it) }
		Mnemonic.JNZ     += { encodeJCC(0x75, 0x850F, it) }
		Mnemonic.JNE     += { encodeJCC(0x75, 0x850F, it) }
		Mnemonic.JBE     += { encodeJCC(0x76, 0x860F, it) }
		Mnemonic.JNA     += { encodeJCC(0x76, 0x860F, it) }
		Mnemonic.JNBE    += { encodeJCC(0x77, 0x870F, it) }
		Mnemonic.JA      += { encodeJCC(0x77, 0x870F, it) }
		Mnemonic.JS      += { encodeJCC(0x78, 0x880F, it) }
		Mnemonic.JNS     += { encodeJCC(0x79, 0x890F, it) }
		Mnemonic.JP      += { encodeJCC(0x7A, 0x8A0F, it) }
		Mnemonic.JPE     += { encodeJCC(0x7A, 0x8A0F, it) }
		Mnemonic.JNP     += { encodeJCC(0x7B, 0x8B0F, it) }
		Mnemonic.JPO     += { encodeJCC(0x7B, 0x8B0F, it) }
		Mnemonic.JL      += { encodeJCC(0x7C, 0x8C0F, it) }
		Mnemonic.JNGE    += { encodeJCC(0x7C, 0x8C0F, it) }
		Mnemonic.JNL     += { encodeJCC(0x7D, 0x8D0F, it) }
		Mnemonic.JGE     += { encodeJCC(0x7D, 0x8D0F, it) }
		Mnemonic.JLE     += { encodeJCC(0x7E, 0x8E0F, it) }
		Mnemonic.JNG     += { encodeJCC(0x7E, 0x8E0F, it) }
		Mnemonic.JNLE    += { encodeJCC(0x7F, 0x8F0F, it) }
		Mnemonic.JG      += { encodeJCC(0x7F, 0x8F0F, it) }
		Mnemonic.RET     += { encode1I(0xC2, WORD, it) }
		Mnemonic.RETF    += { encode1I(0xCA, WORD, it) }
		Mnemonic.RETW    += { encode1I(0xC266, WORD, it) }
		Mnemonic.RETFQ   += { encode1I(0xCA48, WORD, it) }
		Mnemonic.INT     += { encode1I(0xCD, BYTE, it) }
		Mnemonic.LOOP    += { encode1REL(0xE2, BYTE, it) }
		Mnemonic.LOOPE   += { encode1REL(0xE1, BYTE, it) }
		Mnemonic.LOOPZ   += { encode1REL(0xE1, BYTE, it) }
		Mnemonic.LOOPNE  += { encode1REL(0xE0, BYTE, it) }
		Mnemonic.LOOPNZ  += { encode1REL(0xE0, BYTE, it) }
		Mnemonic.JECXZ   += { encode1REL(0xE367, BYTE, it) }
		Mnemonic.JRCXZ   += { encode1REL(0xE3, BYTE, it) }
		Mnemonic.XBEGIN  += { encode1REL(0xF8C7, DWORD, it) }
		Mnemonic.XABORT  += { encode1I(0xF8C6, BYTE, it) }
		Mnemonic.JMPF    += { encode1M(0xFF, 0b1110, 5, it.single, NONE) }
		Mnemonic.CALLF   += { encode1M(0xFF, 0b1110, 3, it.single, NONE) }
		Mnemonic.SHLD    += { encodeSHLD(0xA40F, it) }
		Mnemonic.SHRD    += { encodeSHLD(0xAC0F, it) }
		Mnemonic.ADD     += { encodeADD(0x00, 0, it) }
		Mnemonic.OR      += { encodeADD(0x08, 1, it) }
		Mnemonic.ADC     += { encodeADD(0x10, 2, it) }
		Mnemonic.SBB     += { encodeADD(0x18, 3, it) }
		Mnemonic.AND     += { encodeADD(0x20, 4, it) }
		Mnemonic.SUB     += { encodeADD(0x28, 5, it) }
		Mnemonic.XOR     += { encodeADD(0x30, 6, it) }
		Mnemonic.CMP     += { encodeADD(0x38, 7, it) }
		Mnemonic.ROL     += { encodeROL(0, it) }
		Mnemonic.ROR     += { encodeROL(1, it) }
		Mnemonic.RCL     += { encodeROL(2, it) }
		Mnemonic.RCR     += { encodeROL(3, it) }
		Mnemonic.SAL     += { encodeROL(4, it) }
		Mnemonic.SHL     += { encodeROL(4, it) }
		Mnemonic.SHR     += { encodeROL(5, it) }
		Mnemonic.SAR     += { encodeROL(7, it) }
	}


	/*
	Custom assembly
	 */



	private val InsNode.single get() = if(count != 1) insErr() else op1

	private fun encodeHRESET(node: InsNode) {
		if(node.count != 1 && (node.count == 2 && node.r2 != Reg.EAX)) insErr()
		if(node.op1.type != OpType.IMM) insErr()
		word(0x0FF3)
		i24(0xC0F03A)
		imm(node.op1, BYTE)
	}

	private fun encodeFSTSW(node: InsNode) {
		if(node.mnemonic == Mnemonic.FSTSW) byte(0x9B)
		if(node.count != 1) insErr()
		when {
			node.r1 == Reg.AX -> word(0xE0DF)
			node.op1.isMem    -> encode1MSingle(0xDD, WORD, 7, node.op1)
			else              -> insErr()
		}
	}

	private fun encodeFSAVE(opcode: Int, ext: Int, node: InsNode) {
		if(node.count != 1 || !node.op1.isMem) insErr()
		byte(0x9B)
		encode1MEM(opcode, ext, node.op1)
	}

	private fun encodeSHLD(opcode: Int, node: InsNode) {
		if(node.count != 3) insErr()
		when {
			node.op3.reg == Reg.CL      -> encode2RMR(opcode + (1 shl 8), 0b1110, node.op1, node.op2.reg, NONE)
			node.op3.type == OpType.IMM -> encode2RMR(opcode, 0b1110, node.op1, node.op2.reg, BYTE).imm(node.op3, BYTE)
			else                        -> insErr()
		}
	}

	private fun encodeADD(start: Int, ext: Int, node: InsNode) {
		if(node.count != 2) insErr()
		val op1 = node.op1
		val op2 = node.op2

		if(node.op2.isImm) {
			val imm  = resolve(op2.node)
			fun ai() = encodeNone(start + 4, op1.reg.width).imm(imm, op1.reg.width)
			fun i8() = encode1RM(0x83, 0b1110, ext, op1, BYTE).imm(imm, BYTE)
			fun i()  = encode1RM(0x80, 0b1111, ext, op1, op1.width).imm(imm, op1.width)

			when {
				op1.reg == Reg.AL -> ai()
				op1.width == BYTE -> i()
				op2.width == BYTE -> i8()
				op2.width != NONE -> i()
				imm.hasReloc      -> i()
				imm.isImm8        -> i8()
				op1.reg.isA       -> ai()
				else              -> i()
			}
		} else if(op2.isMem) {
			encode2RM(start + 2, 0b1111, op1.reg, op2, NONE)
		} else if(op1.isMem) {
			encode2RM(start, 0b1111, op2.reg, op1, NONE)
		} else {
			encode2RR(start, 0b1111, op2.reg, op1.reg)
		}
	}

	private fun encodeROL(ext: Int, node: InsNode) {
		if(node.count != 2)
			insErr()

		if(node.r2 == Reg.CL) {
			encode1RM(0xD2, 0b1111, ext, node.op1, NONE)
		} else if(node.op2.isImm) {
			val imm = resolve(node.op2)
			if(!imm.hasReloc && imm.disp == 1L)
				encode1RM(0xD0, 0b1111, ext, node.op1, NONE)
			else
				encode1RM(0xC0, 0b1111, ext, node.op1, BYTE).imm(imm, BYTE)
		} else {
			insErr()
		}
	}

	private fun encodeTEST(node: InsNode) {
		if(node.count != 2) insErr()
		val op1 = node.op1
		val op2 = node.op2

		when(op2.type) {
			OpType.MEM -> encode2RMR(0x84, 0b1111, op2, op1.reg, NONE)
			OpType.IMM -> {
				if(op1.reg.isA)
					encode1I(0xA8, op2, op1.reg.width)
				else
					encode1RM(0xF6, 0b1111, 0, op1, op1.width).imm(op2, op1.width)
			}
			else -> encode2RMR(0x84, 0b1111, op1, op2.reg, NONE)
		}
	}

	private fun encodeIMUL(node: InsNode) {
		val op1 = node.op1
		val op2 = node.op2
		val op3 = node.op3

		when(node.count) {
			1 -> encode1RM(0xF6, 0b1111, 5, op1, NONE)
			2 -> encode2RRM(0xAF0F, 0b1110, op1.reg, op2, NONE)
			3 -> {
				val imm = resolve(op3)

				if(op3.width == BYTE || (op3.width == NONE && !imm.hasReloc && imm.disp.isImm8)) {
					encode2RRM(0x6B, 0b1110, op1.reg, op2, BYTE).imm(imm, BYTE)
				} else {
					encode2RRM(0x69, 0b1110, op1.reg, op2, op1.width).imm(imm, op1.width)
				}
			}
			else -> insErr()
		}
	}

	private fun encodeIN(node: InsNode) {
		if(node.count != 2) insErr()
		when {
			node.r2 == Reg.DX -> when(node.r1) {
				Reg.AL  -> byte(0xEC)
				Reg.AX  -> word(0xED66)
				Reg.EAX -> byte(0xED)
				else    -> insErr()
			}
			node.op2.isImm -> when(node.r1) {
				Reg.AL  -> byte(0xE4).imm(node.op2, BYTE)
				Reg.AX  -> word(0xE566).imm(node.op2, BYTE)
				Reg.EAX -> byte(0xE5).imm(node.op2, BYTE)
				else    -> insErr()
			}
			else -> insErr()
		}
	}

	private fun encodeOUT(node: InsNode) {
		if(node.count != 2) insErr()
		when {
			node.r1 == Reg.DX -> when(node.r2) {
				Reg.AL  -> byte(0xEE)
				Reg.AX  -> word(0xEF66)
				Reg.EAX -> byte(0xEF)
				else    -> insErr()
			}
			node.op1.isImm -> when(node.r2) {
				Reg.AL  -> byte(0xE6).imm(node.op1, BYTE)
				Reg.AX  -> word(0xE766).imm(node.op1, BYTE)
				Reg.EAX -> byte(0xE7).imm(node.op1, BYTE)
				else    -> insErr()
			}
			else -> insErr()
		}
	}

	private fun encodeLEA(node: InsNode) {
		if(node.count != 2 || !node.op2.isMem) insErr()
		encode2RM(0x8D, 0b1110, node.r1, node.op2, NONE)
	}

	private fun encodeENTER(node: InsNode) {
		if(node.count != 2) insErr()
		if(!node.op1.isImm || !node.op2.isImm) insErr()
		val imm1 = resolve(node.op1)
		val imm2 = resolve(node.op2)
		if(imm1.hasReloc || imm2.hasReloc) insErr()
		if(node.mnemonic == Mnemonic.ENTERW) word(0xC877) else byte(0xC8)
		imm(imm1, WORD)
		imm(imm2, BYTE)
	}

	private fun encodeXCHG(node: InsNode) {
		if(node.count != 2) insErr()
		val op1 = node.op1
		val op2 = node.op2

		when {
			op1.reg == Reg.EAX &&
			op2.reg == Reg.EAX -> word(0xC087)
			op1.isMem          -> encode2RM(0x86, 0b1111, op2.reg, op1, NONE)
			op2.isMem          -> encode2RM(0x86, 0b1111, op1.reg, op2, NONE)
			op1.width == BYTE  -> encode2RR(0x86, 0b1111, op1.reg, op2.reg)
			op1.reg.isA        -> encode1O(0x90, 0b1110, op2.reg)
			op2.reg.isA        -> encode1O(0x90, 0b1110, op1.reg)
			else               -> encode2RR(0x86, 0b1111, op1.reg, op2.reg)
		}
	}

	private fun encodeBSWAP(node: InsNode) {
		if(node.count != 1) insErr()
		val op1 = node.op1.reg
		when(op1.type) {
			OpType.R32 -> writeRex(0, 0, 0, op1.rex)
			OpType.R64 -> writeRex(1, 0, 0, op1.rex)
			else       -> insErr()
		}
		word(0xC80F + (op1.value shl 8))
	}

	private fun encodeMOV(ins: InsNode) {
		if(ins.count != 2) insErr()
		val op1 = ins.op1
		val op2 = ins.op2

		if(op2.type == OpType.IMM) {
			val imm = resolve(op2)
			if(op1.type == OpType.MEM) {
				encode1M(0xC6, 0b1111, 0, op1, op1.width).imm(imm, op1.width)
			} else {
				if(op1.type == OpType.R64 && !imm.hasReloc && imm.isImm32) {
					encode1O(0xB0, 0b1111, Reg.r32(op1.reg.index))
					imm(imm, DWORD)
				} else {
					encode1O(0xB0, 0b1111, op1.reg)
					imm(imm, op1.reg.width, imm64 = true)
				}
			}
		} else if(op1.type.isMem) {
			if(op2.type == OpType.SEG)
				encodeMOVMSEG(0x8C, op2.reg, op1)
			else
				encode2RM(0x88, 0b1111, op2.reg, op1, NONE)
		} else if(op2.type.isMem) {
			if(op1.type == OpType.SEG)
				encodeMOVMSEG(0x8C, op1.reg, op2)
			else
				encode2RM(0x8A, 0b1111, op1.reg, op2, NONE)
		} else if(op1.type.isR && op2.type == op1.type) {
			encode2RR(0x88, 0b1111, op2.reg, op1.reg)
		} else when {
			op1.type == OpType.CR  -> encodeMOVRR(0x220F, op1.reg, op2.reg)
			op2.type == OpType.CR  -> encodeMOVRR(0x200F, op2.reg, op1.reg)
			op1.type == OpType.DR  -> encodeMOVRR(0x230F, op1.reg, op2.reg)
			op2.type == OpType.DR  -> encodeMOVRR(0x210F, op2.reg, op1.reg)
			op1.type == OpType.SEG -> encodeMOVRSEG(0x8E, op1.reg, op2.reg)
			op2.type == OpType.SEG -> encodeMOVRSEG(0x8C, op2.reg, op1.reg)
			else -> insErr()
		}
	}

	private fun encodeMOVRR(opcode: Int, op1: Reg, op2: Reg) {
		if(op2.type != OpType.R64) insErr()
		writeRex(0, op1.rex, 0, op2.rex)
		word(opcode)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encodeMOVMSEG(opcode: Int, op1: Reg, op2: OpNode) {
		val mem = resolve(op2)
		writeRex(if(op2.width == QWORD) 1 else 0, 0, mem.rexX, mem.rexB)
		byte(opcode)
		mem.writeMem(op1.value, NONE)
	}

	private fun encodeMOVRSEG(opcode: Int, op1: Reg, op2: Reg) {
		when(op2.type) {
			OpType.R16 -> { byte(0x66); writeRex(0, op1.rex, 0, op2.rex) }
			OpType.R32 -> writeRex(0, op1.rex, 0, op2.rex)
			OpType.R64 -> writeRex(1, op1.rex, 0, op2.rex)
			else       -> insErr()
		}
		byte(opcode)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encodeCALL(node: InsNode) {
		when(node.single.type) {
			OpType.MEM -> encode1MSingle(0xFF, QWORD, 2, node.op1)
			OpType.IMM -> byte(0xE8).rel(node.op1, DWORD)
			else       -> encode1R(0xFF, 0b1000, 2, node.r1)
		}
	}

	private fun encode1Rel832(rel8Opcode: Int, rel32Opcode: Int, op1: OpNode) {
		val imm = resolve(op1)

		fun rel8() = byte(rel8Opcode).rel(imm, BYTE)
		fun rel32() = writer.varLengthInt(rel32Opcode).rel(imm, DWORD)

		when(op1.width) {
			NONE -> when {
				imm.hasReloc -> rel32()
				imm.isImm8   -> rel8()
				else         -> rel32()
			}
			BYTE  -> rel8()
			DWORD -> rel32()
			else  -> widthMismatchErr()
		}
	}

	private fun encodeJMP(node: InsNode) {
		when(node.single.type) {
			OpType.MEM -> encode1MSingle(0xFF, QWORD, 4, node.op1)
			OpType.IMM -> encode1Rel832(0xEB, 0xE9, node.op1)
			else       -> encode1R(0XFF, 0b1000, 4, node.r1)
		}
	}

	private fun encodeJCC(rel8Opcode: Int, rel32Opcode: Int, node: InsNode) {
		if(node.count != 1) insErr()
		encode1Rel832(rel8Opcode, rel32Opcode, node.op1)
	}

	private fun encodePUSH(node: InsNode) {
		when(node.single.type) {
			OpType.R16 -> encode1O(0x50, 0b1010, node.r1)
			OpType.R64 -> encode1O(0x50, 0b1010, node.r1)
			OpType.SEG -> when(node.r1) {
				Reg.FS -> word(0xA00F)
				Reg.GS -> word(0xA80F)
				else   -> insErr()
			}
			OpType.MEM -> encode1M(0xFF, 0b1010, 6, node.op1, NONE)
			OpType.IMM -> {
				val imm = resolve(node.op1)

				fun i32() = byte(0x68).imm(imm, DWORD)
				fun i16() = word(0x6866).imm(imm, WORD)
				fun i8() = byte(0x6A).imm(imm, BYTE)

				when(imm.width) {
					NONE -> when {
						imm.hasReloc -> i32()
						imm.isImm8   -> i8()
						imm.isImm16  -> i16()
						else         -> i32()
					}
					BYTE  -> i8()
					WORD  -> i16()
					DWORD -> i32()
					else  -> insErr()
				}
			}
			else -> insErr()
		}
	}

	private fun encodePOP(node: InsNode) {
		when(node.single.type) {
			OpType.MEM -> encode1M(0x8F, 0b1010, 0, node.op1, NONE)
			OpType.SEG -> when(node.r1) {
				Reg.FS -> word(0xA10F)
				Reg.GS -> word(0xA90F)
				else   -> insErr()
			}
			else -> encode1O(0x58, 0b1010, node.r1)
		}
	}

	private fun encodePUSHW(node: InsNode) {
		when(node.single.reg) {
			Reg.FS -> writer.i24(0xA80F66)
			Reg.GS -> writer.i32(0xA80F66)
			else   -> insErr()
		}
	}

	private fun encodePOPW(node: InsNode) {
		when(node.single.reg) {
			Reg.FS -> writer.i24(0xA10F66)
			Reg.GS -> writer.i32(0xA10F66)
			else   -> insErr()
		}
	}

	private fun encodeDLLCALL(node: InsNode) {
		val op1 = node.single
		val nameNode = op1.node as? NameNode ?: insErr()
		nameNode.sym = context.getDllImport(nameNode.value)
		if(nameNode.sym == null) insErr("Unrecognised dll import: ${nameNode.value}")
		encode1M(0xFF, 0b1000, 2, OpNode.mem(nameNode, QWORD), NONE)
	}

	private fun encodeRETURN() {
		//if(epilogueWriter.isEmpty) insErr("Return invalid here")
		//writer.bytes(epilogueWriter)
	}



	/*
	Auto assembly
	 */



	/**
	 *     wvvv-vlpp_rxbm-mmmm_1100-0100
	 *     r: ~REX.R (ModRM:REG)
	 *     x: ~REX.X (SIB:INDEX)
	 *     b: ~REX.B (SIB:BASE, MODRM:RM, OPREG)
	 */
	private fun AutoEnc.writeVex(r: Int, x: Int, b: Int, vvvv: Int) {
		if(vexw != 0 || escape > 1 || x == 0 || b == 0)
			dword(
				(0xC4 shl 0) or
				(r shl 15) or (x shl 14) or (b shl 13) or (escape shl 8) or
				(vexw shl 23) or (vvvv shl 19) or (vexl shl 18) or (prefix shl 16) or
				(opcode shl 24)
			)
		else
			i24(
				(0xC5 shl 0) or
				(r shl 15) or (vvvv shl 11) or (vexl shl 10) or (prefix shl 8) or
				(opcode shl 16)
			)
	}

	private fun AutoEnc.writeSimdOpcode() { when(escape) {
		0 -> byte(opcode)
		1 -> word(0x0F or (opcode shl 8))
		2 -> i24(0x380F or (opcode shl 16))
		3 -> i24(0x3A0F or (opcode shl 16))
		else -> context.internalErr()
	} }



	private fun AutoEnc.writeSimdPrefix() { when(prefix) {
		0 -> { }
		1 -> byte(0x66)
		2 -> byte(0xF3)
		3 -> byte(0xF2)
		4 -> byte(0x9B)
	}}



	private fun AutoEnc.checkNull() = this.also { if(isNull) insErr() }



	private fun getAutoEnc(mnemonic: Mnemonic, ops: AutoOps): AutoEnc {
		val encs = EncGen.autoEncs[mnemonic.ordinal]

		for(e in encs) {
			val enc = AutoEnc(e)
			if(enc.ops == ops)
				return enc
		}

		if(ops.width != 0)
			return AutoEnc()

		for(e in encs) {
			val enc = AutoEnc(e)
			if(enc.ops.equalsExceptWidth(ops))
				return enc
		}

		return AutoEnc()
	}



	private fun assembleFpu(ins: InsNode) { when {
		ins.count == 1 -> {
			val enc = getAutoEnc(ins.mnemonic, AutoOps.ST).checkNull()
			word(enc.opcode + (ins.op1.reg.value shl 8))
		}

		ins.count != 2 -> insErr()

		ins.op1.reg == Reg.ST0 -> {
			if(ins.op2.type != OpType.ST) insErr()
			var enc = getAutoEnc(ins.mnemonic, AutoOps.ST0_ST)
			if(enc.isNull) {
				if(ins.op2.reg != Reg.ST0) insErr()
				enc = getAutoEnc(ins.mnemonic, AutoOps.ST_ST0).checkNull()
			}
			word(enc.opcode + (ins.op2.reg.value shl 8))
		}

		ins.op2.reg == Reg.ST0 -> {
			val enc = getAutoEnc(ins.mnemonic, AutoOps.ST_ST0).checkNull()
			word(enc.opcode + (ins.op1.reg.value shl 8))
		}

		else -> insErr()
	} }



	private fun assembleAuto(ins: InsNode) {
		var mem = Mem.NULL
		var imm = Mem.NULL
		var immWidth = NONE

		fun check(node: OpNode) {
			if(node.type == OpType.IMM) {
				if(node.width != NONE && node.width != BYTE)
					insErr()
				imm = resolve(node)
				if(!imm.isImm8)
					insErr()
				immWidth = BYTE
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
			mem.width.ordinal,
			mem.vsib,
			0
		)

		val enc = getAutoEnc(ins.mnemonic, ops).checkNull()

		var r: Reg
		val m: Reg
		val v: Reg

		val r1 = ins.op1.reg
		val r2 = ins.op2.reg
		val r3 = ins.op3.reg

		when(enc.opEnc) {
			OpEnc.RMV.ordinal -> { r = r1; m = r2; v = r3 }
			OpEnc.RVM.ordinal -> { r = r1; v = r2; m = r3 }
			OpEnc.MRV.ordinal -> { m = r1; r = r2; v = r3 }
			OpEnc.MVR.ordinal -> { m = r1; v = r2; r = r3 }
			OpEnc.VMR.ordinal -> { v = r1; m = r2; r = r3 }
			else -> context.internalErr()
		}

		if(r == Reg.NONE)
			r = Reg.r32(enc.ext)
		if(enc.o16 == 1)
			byte(0x66)
		if(enc.a32 == 1)
			byte(0x67)

		if(enc.vex == 1) {
			if(mem != Mem.NULL) {
				if(mem.a32) byte(0x67)
				enc.writeVex(r.vexRex, mem.vexX, mem.vexB, v.vValue)
				mem.writeMem(r.value, immWidth)
			} else {
				enc.writeVex(r.vexRex, 1, m.vexRex, v.vValue)
				byte(0b11_000_000 or (r.value shl 3) or (m.value))
			}
		} else {
			if(mem != Mem.NULL) {
				if(mem.a32) byte(0x67)
				enc.writeSimdPrefix()
				writeRex(enc.rw, r.rex, mem.rexX, mem.rexB, r.rex8 or m.rex8, r.noRex or m.noRex)
				enc.writeSimdOpcode()
				mem.writeMem(r.value, immWidth)
			} else {
				enc.writeSimdPrefix()
				writeRex(enc.rw, r.rex, 0, m.rex, r.rex8 or m.rex8, r.noRex or m.noRex)
				enc.writeSimdOpcode()
				byte(0b11_000_000 or (r.value shl 3) or (m.value))
			}
		}

		when {
			ins.op4.reg != Reg.NONE -> byte(ins.op4.reg.index shl 4)
			imm != Mem.NULL -> imm(imm, BYTE)
			enc.pseudo > 0 -> byte(enc.pseudo - 1)
		}
	}


}