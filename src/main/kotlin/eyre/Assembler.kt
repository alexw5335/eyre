package eyre

import eyre.Width.QWORD
import eyre.Width.WORD
import eyre.util.NativeWriter

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private val dataWriter = context.dataWriter

	private val epilogueWriter = NativeWriter()

	private var writer = textWriter

	private var section = Section.TEXT

	private var rexRequired = false

	private var rexDisallowed = false



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {

			}
		}
	}



	private fun invalidEncoding(): Nothing = error("Invalid encoding")




	/*
	Relocations
	 */



	private fun addLinkReloc(width: Width, node: AstNode) {
		context.relocs.add(Reloc(
			writer.pos,
			section,
			width,
			node,
			0,
			RelocType.LINK
		))
	}

	private fun addAbsReloc(node: AstNode) {
		context.relocs.add(Reloc(
			writer.pos,
			section,
			QWORD,
			node,
			0,
			RelocType.ABS
		))
		context.absRelocCount++
	}

	private fun addRelReloc(width: Width, node: AstNode, offset: Int) {
		context.relocs.add(Reloc(
			writer.pos,
			section,
			width,
			node,
			offset,
			RelocType.RIP
		))
	}



	/*
	Writing
	 */



	private val OpNode.asReg get() = (this as? RegNode)?.value ?: invalidEncoding()

	private val OpNode.asMem get() = this as? MemNode ?: invalidEncoding()

	private val OpNode.asImm get() = this as? ImmNode ?: invalidEncoding()

	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun dword(value: Int) = writer.i32(value)

	private fun checkWidths(mask: OpMask, width: Width) {
		if(width !in mask) invalidEncoding()
	}

	private fun checkWidths(op1: Reg, op2: Reg) {
		if(op1.width != op2.width) invalidEncoding()
	}

	private fun checkWidths(op1: Reg, op2: MemNode) {
		if(op2.width != null && op2.width != op1.width) invalidEncoding()
	}

	private fun writeO16(mask: OpMask, width: Width) {
		if(mask != OpMask.WORD && width == WORD) writer.i8(0x66)
	}

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int) {
		val value = 0b0100_0000 or (w shl 3) or (r shl 2) or (x shl 1) or b
		if(rexRequired || value != 0b0100_0000)
			if(rexDisallowed)
				invalidEncoding()
			else
				writer.i8(value)
	}

	private fun writeModRM(mod: Int, reg: Int, rm: Int) {
		writer.i8((mod shl 6) or (reg shl 3) or rm)
	}

	private fun writeSib(scale: Int, index: Int, base: Int) {
		writer.i8((scale shl 6) or (index shl 3) or base)
	}

	/** Return 1 if width is QWORD and widths has DWORD set, otherwise 0 */
	private fun rexW(mask: OpMask, width: Width) =
		(width.bytes shr 3) and (mask.value shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun getOpcode(opcode: Int, widths: OpMask, width: Width) =
		opcode + ((widths.value and 1) and (1 shl width.ordinal).inv())

	private fun preRex(opcode: Opcode, mask: OpMask, width: Width) {
		checkWidths(mask, width)
		writeO16(mask, width)
		writePrefix(opcode)
	}

	private fun writePrefix(opcode: Opcode) {
		when(opcode.prefix) {
			Opcode.P66 -> byte(0x66)
			Opcode.P67 -> byte(0x67)
			Opcode.PF2 -> byte(0xF2)
			Opcode.PF3 -> byte(0xF3)
		}
	}

	private fun writeEscape(opcode: Opcode) {
		when(opcode.escape) {
			Opcode.E0F -> byte(0x0F)
			Opcode.E00 -> word(0x000F)
			Opcode.E38 -> word(0x380F)
			Opcode.E3A -> word(0x3A0F)
		}
	}

	private fun writeOpcode(opcode: Opcode) {
		writeEscape(opcode)
		writer.varLengthInt(opcode.opcode)
	}

	private fun writeOpcode(opcode: Opcode, mask: OpMask, width: Width) {
		writeEscape(opcode)
		writer.varLengthInt(getOpcode(opcode.opcode, mask, width))
	}

	private fun testing(
		opcode: Opcode,
		mask  : OpMask,
		width : Width,
		rex   : Rex,
		modrm : ModRM
	) {
		checkWidths(mask, width)
		writeO16(mask, width)
		writePrefix(opcode)
		if(rex.forced || rex.rex != 0)
			if(rex.banned)
				invalidEncoding()
			else
				byte(0b0100_000 or rex.rex)
		byte(modrm.modrm)
	}



	/*
	Encoding
	 */



	private fun encodeNone(opcode: Opcode, mask: OpMask, width: Width) {
		preRex(opcode, mask, width)
		writeRex(rexW(mask, width), 0, 0, 0)
		writeOpcode(opcode, mask, width)
	}



	private fun encode1R(opcode: Opcode, mask: OpMask, op1: Reg, extension: Int) =
		testing(
			opcode,
			mask,
			op1.width,
			Rex(0, 0, 0, op1.rex, op1.rex8, op1.noRex),
			ModRM(0b11, extension, op1.value)
		)

	private fun encode2RR(opcode: Opcode, mask: OpMask, op1: Reg, op2: Reg) =
		testing(
			opcode,
			mask,
			op1.width,
			Rex(0, op2.rex, 0, op1.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex),
			ModRM(0b11, op2.value, op1.value)
		)






}