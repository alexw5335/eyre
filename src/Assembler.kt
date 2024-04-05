package eyre

import kotlin.math.max
import kotlin.random.Random

class Assembler(private val context: Context) {


	private lateinit var file: SrcFile

	private var writer = context.textWriter

	private var section = context.textSec

	private var nodeIndex = 0

	private var currentFun: FunNode? = null

	private var currentSrcPos: SrcPos? = null

	private fun invalid(): Nothing = throw EyreError(currentSrcPos, "Invalid encoding")

	private fun err(message: String = "No reason given"): Nothing = throw EyreError(currentSrcPos, message)

	private val instructions = ArrayList<Instruction>()

	private fun ins(mnemonic: Mnemonic, op1: Operand? = null, op2: Operand? = null) =
		instructions.add(Instruction(mnemonic, op1, op2))



	/*
	Assembly
	 */



	fun assembleFile(file: SrcFile) {
		this.nodeIndex = 0
		this.file = file

		try {
			assembleScope()
		} catch(e: EyreError) {
			context.errors.add(e)
			file.invalid = true
		}
	}



	private fun assembleScope() {
		while(nodeIndex < file.nodes.size) {
			val node = file.nodes[nodeIndex++]
			if(node !is ScopeEndNode)
				currentSrcPos = node.srcPos
			when(node) {
				is ScopeEndNode  -> return
				is NamespaceNode -> assembleScope()
				is FunNode       -> assembleFunction(node)
				is VarNode       -> { }
				is CallNode      -> { }
				is StructNode    -> { }
				is BinNode       -> assembleBinNode(node)
				else             -> err("Invalid node: $node")
			}
		}
	}



	/*
	Code generation
	 */



	private fun VarNode.width() = when(size) {
		1    -> Width.BYTE
		2    -> Width.WORD
		4    -> Width.DWORD
		8    -> Width.QWORD
		else -> Width.NONE
	}

	private fun assembleFunction(function: FunNode) {
		currentFun = function

		function.stackPos = -32
		for(param in function.params) {
			function.stackPos -= param.size
			param.mem = StackOperand(function.stackPos, param.width())
		}
		for(local in function.locals) {
			function.stackPos -= local.size
			local.mem = StackOperand(function.stackPos, local.width())
		}

		assembleScope()
	}



	private fun allocateStack(size: Int): Int {
		val function = currentFun!!
		val disp = function.stackPos
		function.stackPos -= size
		return disp
	}



	private fun assembleBinNode(node: BinNode) {
		if(node.op != BinOp.SET) invalid()
		preGenExpr(node.right, null)
		genExpr(node.right)
		instructions.forEach { println(it.printString) }
	}



	private fun genCall(call: CallNode) {
		val function = call.receiver ?: invalid()
		if(call.args.size != function.params.size) invalid()
		for((argIndex, arg) in call.args.withIndex()) {
			val param = function.params[argIndex]
			val argType = arg.exprType ?: invalid()
			if(!checkTypes(param.type, argType)) invalid()
		}
	}

	private fun checkTypes(a: Type, b: Type): Boolean = when(a) {
		is PointerType -> b is PointerType && checkTypes(a.type, b.type)
		is IntType     -> b is IntType
		else           -> a == b
	}



	/*
	AST generation
	 */



	private var availableRegs = 0b00001111_00000111

	private fun allocReg(): Reg {
		if(availableRegs == 0) error("Spill")
		val reg = availableRegs.countTrailingZeroBits()
		availableRegs = availableRegs xor (1 shl reg)
		return Reg.r64(reg)
	}

	private fun freeReg(reg: Reg) {
		availableRegs = availableRegs or (1 shl reg.index)
	}

	private fun isAvailable(reg: Reg) = availableRegs and (1 shl reg.index) == 1

	private fun isUsed(reg: Reg) = availableRegs and (1 shl reg.index) == 0


	private fun genExpr(node: Node): Reg {
		instructions.clear()
		val dst = allocReg()
		genExprRec(dst, node)
		freeReg(dst)
		return dst
	}

	private fun preGenExpr(node: Node, parentOp: BinOp?) {
		when(node) {
			is IntNode -> node.isLeaf = true
			is NameNode -> node.isLeaf = true
			is BinNode -> {
				val left = node.left
				val right = node.right
				val op = node.op

				preGenExpr(left, op)
				preGenExpr(right, op)

				if(left.isLeaf) {
					if(right.isLeaf) {
						if(op.isCommutative && (parentOp == op)) {
							node.isLeaf = true
							node.numRegs = 0
						} else {
							node.numRegs = 1
						}
					} else {
						if(op.isCommutative) {
							node.numRegs = right.numRegs
						} else {
							node.numRegs = max(2, node.right.numRegs)
						}
					}
				} else if(right.isLeaf) {
					node.numRegs = left.numRegs
				} else {
					node.numRegs = if(left.numRegs != right.numRegs)
						max(left.numRegs, right.numRegs)
					else
						left.numRegs + 1
				}
			}
			else -> TODO()
		}
	}



	private fun genExprRec(dst: Reg, node: Node) {
		when(node) {
			is BinNode -> {
				val left = node.left
				val right = node.right
				val op = node.op

				if(left.isLeaf) {
					if(right.isLeaf) {
						genMov(dst, node)
					} else {
						if(op.isCommutative) {
							genExprRec(dst, right)
							genBinOp(op, dst, left)
						} else {
							val next = allocReg()
							genExprRec(next, right)
							genMov(dst, left)
							genBinOp(op, dst, next)
							freeReg(next)
						}
					}
				} else if(right.isLeaf) {
					genExprRec(dst, left)
					genBinOp(op, dst, right)
				} else {
					if(op.isCommutative && right.numRegs > left.numRegs) {
						genExprRec(dst, right)
						val next = allocReg()
						genExprRec(next, left)
						genBinOp(op, dst, next)
						freeReg(next)
					} else {
						genExprRec(dst, left)
						val next = allocReg()
						genExprRec(next, right)
						genBinOp(op, dst, next)
						freeReg(next)
					}
				}
			}
			else -> TODO()
		}
	}



	private val BinOp.mnemonic get() = when(this) {
		BinOp.ADD -> Mnemonic.ADD
		BinOp.SUB -> Mnemonic.SUB
		BinOp.MUL -> Mnemonic.IMUL
		BinOp.DIV -> Mnemonic.IDIV
		else -> TODO()
	}

	private fun genBinOp(op: BinOp, dst: Reg, src: Reg) {
		ins(op.mnemonic, RegOperand(dst), RegOperand(src))
	}

	private fun genMemberAccess(dst: Reg, node: DotNode) {
		val leftSym = node.left.exprSym
		if(leftSym is VarNode) {

		} else {
			genMemberAccess(dst, node.left as DotNode)
		}
	}



	private fun genDiv(dst: Reg, src: Reg) {
		val saveRax = dst != Reg.RAX
		if(isUsed(Reg.RAX)) {
			ins(Mnemonic.PUSH, RegOperand(Reg.RAX))
		}
	}



	private fun genBinOp(op: BinOp, dst: Reg, src: Node) {
		when(src) {
			is NameNode -> {
				val sym = src.exprSym ?: invalid()
				if(sym !is VarNode) invalid()
				if(src.exprType != IntTypes.I32) invalid()
				ins(op.mnemonic, RegOperand(dst), sym.mem!!)
			}

			is IntNode -> ins(op.mnemonic, RegOperand(dst), ImmOperand(src.value))
			is BinNode -> {
				if(src.isConst) {
					ins(op.mnemonic, RegOperand(dst), ImmOperand(src.constValue))
				} else {
					genBinOp(src.op, dst, src.left)
					genBinOp(op, dst, src.right)
				}
			}
			else -> error("Invalid node: $src")
		}
	}

	private fun genMov(dst: Reg, src: Node) {
		when(src) {
			is NameNode -> {
				val sym = src.exprSym ?: invalid()
				if(sym !is VarNode) invalid()
				if(src.exprType != IntTypes.I32) invalid()
				ins(Mnemonic.MOV, RegOperand(dst), sym.mem!!)
			}

			is IntNode -> ins(Mnemonic.MOV, RegOperand(dst), ImmOperand(src.value))
			is BinNode -> {
				if(src.isConst) {
					ins(Mnemonic.MOV, RegOperand(dst), ImmOperand(src.constValue))
				} else {
					genMov(dst, src.left)
					genBinOp(src.op, dst, src.right)
				}
			}
			else -> error("Invalid node: $src")
		}
	}



	/*
	Testing
	 */



	private val regValues = LongArray(16)

	private fun evalExpr(node: Node): Long = when(node) {
		is IntNode -> node.value
		is BinNode -> node.op.calc(evalExpr(node.left), evalExpr(node.right))
		else       -> error("Invalid node: $node")
	}

	private fun evalIns(ins: Instruction) {
		val dst = (ins.op1 as RegOperand).reg

		val srcValue = when(ins.op2) {
			is RegOperand -> regValues[ins.op2.reg.index]
			is ImmOperand -> ins.op2.value
			else          -> err()
		}

		when(ins.mnemonic) {
			Mnemonic.MOV  -> regValues[dst.index] = srcValue
			Mnemonic.ADD  -> regValues[dst.index] += srcValue
			Mnemonic.SUB  -> regValues[dst.index] -= srcValue
			Mnemonic.IMUL -> regValues[dst.index] *= srcValue
			Mnemonic.IDIV -> regValues[dst.index] /= srcValue
			Mnemonic.AND  -> regValues[dst.index] = regValues[dst.index] and srcValue
			Mnemonic.XOR  -> regValues[dst.index] = regValues[dst.index] xor srcValue
			Mnemonic.OR   -> regValues[dst.index] = regValues[dst.index] or srcValue
			else          -> error("Invalid mnemonic: ${ins.mnemonic}")
		}
	}

	private val binOps = arrayOf(BinOp.ADD, BinOp.SUB, BinOp.MUL)

	fun randomExpr(minDepth: Int = 2, maxDepth: Int = 3, depth: Int = 0): Node {
		fun bin() = BinNode(
			binOps.random(),
			randomExpr(minDepth, maxDepth, depth + 1),
			randomExpr(minDepth, maxDepth, depth + 1)
		)

		fun int() = IntNode(Random.nextLong(9) + 1)

		return when {
			depth == maxDepth -> int()
			depth < minDepth -> bin()
			Random.nextBoolean() -> int()
			else  -> bin()
		}
	}

	fun testExpr(node: Node): Boolean {
		val expectedValue: Long
		val generatedValue: Long

		try {
			println(node.exprString)
			preGenExpr(node, null)
			printFullExpr(node)
			val dst = genExpr(node)
			expectedValue = evalExpr(node)
			instructions.forEach(::evalIns)
			generatedValue = regValues[dst.index]
			for(ins in instructions) println(ins.printString)
			if(generatedValue != expectedValue) {
				System.err.println("Test failed. Expected: $expectedValue. Generated: $generatedValue")
				return false
			}
			println("Test passed\n")
		} catch(e: ArithmeticException) {
			println("Divide by zero, aborting")
		}
		return true
	}


}