package eyre

class Assembler(private val context: Context) {


	private var currentIns: InsNode? = null

	private var writer = context.textWriter



	fun assemble() {
		for(file in context.files) {
			for(node in file.nodes) {
				try {
					when(node) {
						is VarNode -> handleVarNode(node)
						is InsNode -> handleInsNode(node)
						else -> continue
					}
				} catch(e: EyreError) {
					file.invalid = true
					continue
				}
			}
		}
	}

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(message, srcPos)

	private fun invalid(message: String = "Invalid encoding"): Nothing =
		context.err(message, currentIns!!.srcPos)

	private class ResolvedOp {
		var node: Node? = null
		var width = Width.NONE
		var disp = 0
		var base = Reg.NONE
		var index = Reg.NONE
		var scale = 0
		var relocs = 0
	}



	private var numRelocs = 0
	private var memOp = ResolvedOp()
	private var immOp = ResolvedOp()
	val hasIndex get() = memOp.index != Reg.NONE
	val hasBase get() = memOp.base != Reg.NONE



	private fun resolveMem(node: OpNode) {
		numRelocs = 0
		memOp.node = node
		memOp.width = node.width
		memOp.base = Reg.NONE
		memOp.index = Reg.NONE
		memOp.scale = 0
		memOp.disp = resolveRec(node.child, true)
		memOp.relocs = numRelocs
		memPostResolve()
	}



	private fun resolveImm(node: Node, width: Width) {
		numRelocs = 0
		immOp.node = node
		immOp.width = width
		immOp.disp = resolveRec(node, false)
		immOp.relocs = numRelocs
	}



	private fun resolveRec(node: Node, regValid: Boolean): Int {
		if(node is IntNode)
			return node.value

		if(node is UnNode)
			return node.op.calc(resolveRec(node.child, node.op.regValid or regValid))

		if(node is BinNode) {
			fun index(reg: Reg, other: Node) {
				if(!regValid)
					err(node.srcPos, "Register not valid here")
				if(other !is IntNode)
					err(node.srcPos, "Index register requires int literal as scale")
				memOp.index = reg
				memOp.scale = other.value
			}

			if(node.op == BinOp.MUL)
				if(node.left is RegNode)
					index(node.left.value, node.right)
				else if(node.right is RegNode)
					index(node.right.value, node.left)

			return node.op.calc(
				resolveRec(node.left, node.op.leftRegValid or regValid),
				resolveRec(node.right, node.op.rightRegValid or regValid)
			)
		}

		if(node is RegNode) {
			if(!regValid)
				err(node.srcPos, "Register not valid here")
			if(hasBase) {
				if(hasIndex) {
					err(node.srcPos, "Too many registers")
				} else {
					memOp.index = node.value
					memOp.scale = 1
				}
			} else {
				memOp.base = node.value
			}
		}

		err(node.srcPos, "Invalid node")
	}



	private fun memPostResolve() {
		val index = memOp.index
		val base = memOp.base
		val scale = memOp.scale

		// Index cannot be ESP/RSP, swap to base if possible
		// EBP/RBP/R13/R13D produce longer instructions as bases, swap if possible.
		if(hasIndex) {
			if(index.isInvalidIndex) {
				if(scale != 1 || base.isInvalidIndex)
					error("Index register cannot be ESP/RSP")
				memOp.index.let { memOp.index = memOp.base; memOp.base = it }
			} else if(base.isImperfectBase && scale == 1) {
				memOp.index.let { memOp.index = memOp.base; memOp.base = it }
			}
		}

		// 1: [R*1] -> [R], avoid SIB
		// 2: [R*2] -> [R+R*1], avoid index-only SIB which produces DISP32 of zero
		// 3: [R*3] -> [R+R*2], [R+R*3] -> invalid
		// 5: [R*5] -> [R+R*4], [R+R*5] -> invalid
		when(scale) {
			1 -> if(!hasBase) { memOp.base = memOp.index; memOp.index = Reg.NONE; memOp.scale = 0 }
			2 -> if(!hasBase) { memOp.base = memOp.index; memOp.scale = 1 }
			3 -> if(!hasBase) { memOp.base = memOp.index; memOp.scale = 2 } else invalid("Invalid scale: 3")
			5 -> if(!hasBase) { memOp.base = memOp.index; memOp.scale = 4 } else invalid("Invalid scale: 5")
			else -> invalid("Invalid scale: $scale")
		}
	}



	private fun writeInitialiser(type: Type, offset: Int, node: Node) {
		if(node is InitNode) {
			if(type is StructNode) {
				if(node.elements.size > type.members.size)
					err(node.srcPos, "Too many initialiser elements")
				for(i in node.elements.indices) {
					val member = type.members[i]
					writeInitialiser(member.type, offset + member.offset, node.elements[i])
				}
			} else if(type is ArrayType) {
				if(node.elements.size > type.count)
					err(node.srcPos, "Too many initialiser elements. Found: ${node.elements.size}, expected: < ${type.count}")
				for(i in node.elements.indices)
					writeInitialiser(type.base, offset + type.base.size * i, node.elements[i])
			} else {
				err(node.srcPos, "Invalid initialiser type: ${type.name}")
			}
		} else {
			if(type is IntType) {
				resolveImm(node, Width.NONE)
				val value = immOp.disp
				writer.at(writer.pos + offset) {
					when(type.size) {
						1 -> writer.i8(value)
						2 -> writer.i16(value)
						4 -> writer.i32(value)
						else -> err(node.srcPos, "Invalid initialiser size: ${type.size}")
					}
				}
			} else {
				err(node.srcPos, "Invalid initialiser")
			}
		}
	}



	private fun handleVarNode(varNode: VarNode) {
		writeInitialiser(varNode.type!!, 0, varNode.valueNode!!)
		writer.pos += varNode.type!!.size
	}




	private fun handleInsNode(node: InsNode) {
		when(node.count) {
			0 -> when(node.mnemonic) {
				Mnemonic.RET -> byte(0xC3)
				else -> invalid()
			}
			1 -> when(node.mnemonic) {
				Mnemonic.NOT -> { }
				else -> invalid()
			}
			2 -> when(node.mnemonic) {
				else -> invalid()
			}
			else -> invalid()
		}
	}



	/*
	Encoding Utils
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
				invalid("REX prefix not allowed here")
			else
				byte(0b0100_0000 or value)
	}

	private fun checkWidth(mask: Int, width: Int) {
		if((1 shl width) and mask == 0)
			invalid("Invalid operand width")
	}

	private fun writeO16(mask: Int, width: Int) {
		if(mask != 0b10 && width == 1) writer.i8(0x66)
	}

	private fun writeA32(mem: Mem) {
		if(mem.vsib != 0) invalid("VSIB not valid here")
		if(mem.aso == 1) byte(0x67)
	}

	/** Return 1 if width is QWORD (3) and widths has DWORD (2) set, otherwise 0 */
	private fun rexw(mask: Int, width: Int) = ((1 shl width) shr 3) and (mask shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun writeOpcode(opcode: Int, mask: Int, width: Int) {
		val addition = (mask and 1) and (1 shl width).inv()
		if(opcode and 0xFF00 != 0) word(opcode + (addition shl 8)) else byte(opcode + addition)
	}

	private fun ResolvedOp.writeMem(reg: Int, immWidth: Width) {
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


}