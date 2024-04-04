package eyre

import kotlin.math.max
import kotlin.random.Random
import kotlin.system.exitProcess

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
		allocExpr(node.right)
		println(node.exprString)
		printFullExpr(node.right)
		genExpr(node.right)
		for(ins in instructions)
			println(ins.printString)
		val result = testExpr(node.right) ?: return
		if(result.expected != result.generated)
			System.err.println("Expected: ${result.expected}. Generated: ${result.generated}")
		else
			println("Test passed")
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


	private var mustInit = false

	private var volatileRegs = 0b00001111_00000111

	private fun nextReg(): Reg {
		if(volatileRegs == 0) error("Spill")
		val reg = volatileRegs.countTrailingZeroBits()
		return Reg.r64(reg)
	}

	private fun allocReg(): Reg {
		if(volatileRegs == 0) error("Spill")
		val reg = volatileRegs.countTrailingZeroBits()
		volatileRegs = volatileRegs xor (1 shl reg)
		return Reg.r64(reg)
	}

	private fun freeReg(reg: Reg) {
		volatileRegs = volatileRegs or (1 shl reg.index)
	}

	private fun isAvailable(reg: Reg) = volatileRegs and (1 shl reg.index) == 1



	private fun allocExpr(node: Node) {
		allocExprRec(node, null)
		freeReg(node.reg!!)
	}

	private fun genExpr(node: Node) {
		instructions.clear()
		genExprRec(node, false)
	}

	private fun allocExprRec(node: Node, parentOp: BinOp?) {
		when(node) {
			is CallNode -> {
				val dst = StackOperand(allocateStack(8))
				node.mem = dst
				genCall(node)
				ins(Mnemonic.MOV, dst, RegOperand(Reg.RAX))
			}
			is IntNode -> node.isLeaf = true
			is BinNode -> {
				val left = node.left
				val right = node.right
				val op = node.op

				allocExprRec(left, op)
				allocExprRec(right, op)

				if(left.isLeaf) {
					if(right.isLeaf) {
						if(op.isCommutative && (parentOp == op)) {
							node.isRegless = true
							node.isLeaf = true
							node.numRegs = 0
						} else {
							node.reg = allocReg()
							node.numRegs = 1
						}
					} else {
						if(op.isCommutative) {
							node.reg = right.reg
							node.numRegs = right.numRegs
						} else {
							node.reg = allocReg()
							node.numRegs = max(2, node.right.numRegs)
							freeReg(right.reg!!)
						}
					}
				} else if(right.isLeaf) {
					node.reg = left.reg
					node.numRegs = left.numRegs
				} else {
					node.reg = left.reg
					freeReg(right.reg!!)
					node.numRegs = if(left.numRegs != right.numRegs)
						max(left.numRegs, right.numRegs)
					else
						left.numRegs + 1
				}
			}
			else -> TODO()
		}
	}



	private fun genExprRec(node: Node, mustInit: Boolean) {
		if(node !is BinNode) TODO()

		val left = node.left
		val right = node.right
		val op = node.op
		val dst = node.reg!!

		if(left.isLeaf) {
			if(right.isLeaf) {
				if(!node.isRegless || mustInit) {
					genMovRegNode(dst, left)
					genBinRegNode(op, dst, right)
				} else {
					genBinRegNode(op, dst, left)
					genBinRegNode(op, dst, right)
				}
			} else {

				if(!op.isCommutative) {
					if(left !is BinNode)
						genMovRegNode(dst, left)
					else
						genExprRec(left, true)
					genExprRec(right, false)
					genBinRegNode(op, dst, right)
				} else {
					genExprRec(right, false)
					genBinRegNode(op, dst, left)
				}
			}
		} else if(right.isLeaf) {
			genExprRec(left, false)
			genBinRegNode(op, dst, right)
		} else {
			genExprRec(left, false)
			genExprRec(right, false)
			genBinRegReg(op, dst, right.reg!!)
		}
	}



	private val BinOp.mnemonic get() = when(this) {
		BinOp.ADD -> Mnemonic.ADD
		BinOp.SUB -> Mnemonic.SUB
		BinOp.MUL -> Mnemonic.IMUL
		BinOp.DIV -> Mnemonic.IDIV
		else -> TODO()
	}

	private val Reg.op get() = RegOperand(this)

	private fun genXchgRaxReg(src: Reg) {
		ins(Mnemonic.XCHG, Reg.RAX.op, src.op)
	}

	private fun genBinRegReg(op: BinOp, dst: Reg, src: Reg) {
		ins(op.mnemonic, RegOperand(dst), RegOperand(src))
	}

	private fun genBinRegNode(op: BinOp, dst: Reg, src: Node) {
		when(src) {
			//is SymNode -> insRM(op.mnemonic, dst, src.sym)
			is IntNode -> ins(op.mnemonic, RegOperand(dst), ImmOperand(src.value))
			is BinNode -> { src.reg = dst; genExprRec(src, false) }
			else       -> error("Invalid node: $src")
		}
	}

	private fun genMovRegNode(dst: Reg, src: Node) {
		when(src) {
		//	is SymNode -> insRM(Mnemonic.MOV, dst, src.sym)
			is IntNode -> ins(Mnemonic.MOV, RegOperand(dst), ImmOperand(src.value))
			is BinNode -> { src.reg = dst; genExprRec(src, true) }
			else       -> error("Invalid node: $src")
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

	class TestResult(val node: Node, val expected: Long, val generated: Long, val zeroDivide: Boolean)

	fun testExpr(node: Node): TestResult {
		val trueValue: Long
		val generatedValue: Long

		try {
			trueValue = evalExpr(node)
			instructions.forEach(::evalIns)
			generatedValue = regValues[node.reg!!.index]
		} catch(e: ArithmeticException) {
			return TestResult(node, 0, 0, true)
		}

		return TestResult(node, trueValue, generatedValue, false)
	}

	fun testAndPrintExpr(node: Node): Boolean {
		println(node.exprString)
		allocExpr(node)
		printFullExpr(node)
		genExpr(node)
		for(ins in instructions) println(ins.printString)
		val result = testExpr(node)
		if(result.zeroDivide) {
			System.err.println("Zero divide")
			return false
		} else if(result.generated != result.expected) {
			System.err.println("Test failed. Expected: ${result.expected}. Generated: ${result.generated}")
			return true
		} else {
			println("Test passed\n")
			return false
		}
	}


}