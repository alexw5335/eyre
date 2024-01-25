package eyre

class Assembler(private val context: Context) {


	private var writer = context.textWriter

	private var section = context.textSec



	fun assemble() {
		for(file in context.files) {
			for(node in file.nodes) {
				try {
					when(node) {
						is VarNode -> handleVarNode(node)
						is FunNode -> handleFunNode(node)
						is ScopeEndNode -> handleScopeEndNode(node)
						else -> continue
					}
				} catch(e: EyreError) {
					file.invalid = true
					continue
				}
			}
		}

		instructions.forEach(::println)
	}



	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(message, srcPos)



	private fun resolveRec(node: Node): Int = when(node) {
		is IntNode -> node.value
		is UnNode  -> node.calc(::resolveRec)
		is BinNode -> node.calc(::resolveRec)
		else       -> err(node.srcPos, "Invalid node")
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
				val value = resolveRec(node)
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
		if(varNode.mem.type == Mem.Type.GLOBAL) {
			writeInitialiser(varNode.type!!, 0, varNode.valueNode!!)
			writer.pos += varNode.type!!.size
		} else {
			varNode.mem.pos = 32
			//varNode.valueNode?.let { handleSet(varNode, it) }
		}
	}

	/*private fun handleFunNode(funNode: FunNode) {
		byte(0x55) // push rbp
		i24(0xE5_89_48) // mov rbp, rsp
		i24(0xEC_83_48); byte(funNode.frameSize) // sub rsp, frameSize

		var paramPos = -8
		for((i, param) in funNode.params.withIndex()) {
			param.mem.type = Mem.Type.STACK
			param.mem.pos = paramPos
			paramPos -= 8
			// MOV DWORD [RSP + paramPos],
		}
	}

	private fun handleScopeEndNode(scopeEndNode: ScopeEndNode) {
		if(scopeEndNode.origin !is FunNode) return
		word(0xC3C9) // leave; ret
	}*/



	/*
	Assembly
	 */



	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun i24(value: Int) = writer.i24(value)

	private fun dword(value: Int) = writer.i32(value)

	private val instructions = ArrayList<Instruction>()

	private fun ins(
		mnemonic: Mnemonic,
		op1: Operand = NullOperand,
		op2: Operand = NullOperand,
	) = instructions.add(Instruction(mnemonic, op1, op2))

	fun paramReg(i: Int) = when(i) {
		0 -> RegOperand.RCX
		1 -> RegOperand.RDX
		2 -> RegOperand.R8
		3 -> RegOperand.R9
		else -> error("Too many params")
	}

	private fun handleFunNode(funNode: FunNode) {
		var frameSize = 32
		for(local in funNode.locals)
			frameSize += (local.size + 7) and -8

		ins(Mnemonic.PUSH, RegOperand.RBP)
		ins(Mnemonic.MOV, RegOperand.RBP, RegOperand.RSP)
		ins(Mnemonic.SUB, RegOperand.RSP, IntOperand(frameSize))

		var paramPos = -8
		for((i, param) in funNode.params.withIndex()) {
			param.mem.type = Mem.Type.STACK
			param.mem.pos = paramPos
			ins(Mnemonic.MOV, StackOperand(Width.DWORD, paramPos), paramReg(i))
			paramPos -= 8
		}
	}

	private fun handleScopeEndNode(scopeEndNode: ScopeEndNode) {
		if(scopeEndNode.origin !is FunNode) return
		ins(Mnemonic.LEAVE)
		ins(Mnemonic.RET)
	}


}