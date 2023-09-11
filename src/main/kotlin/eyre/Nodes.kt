package eyre




sealed class AstNode {
	var srcPos: SrcPos? = null
	var resolved = false // Used by Symbol
	var resolving = false // Used by Symbol
}


/** A node that references a symbol */
sealed interface SymNode {
	val symbol: Symbol?
}



interface Symbol {
	val scope: Scope
	val name: Name
	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"
}



interface IntSymbol : Symbol {
	val intValue: Long
}



interface ScopedSymbol : Symbol {
	val thisScope: Scope
}



interface PosSymbol : Symbol {
	var pos: Int
	var section: Section
}



data object NullNode : AstNode()

class ScopeEnd(val symbol: Symbol?): AstNode()



class DllImportSymbol(
	override val scope: Scope,
	override val name: Name
) : PosSymbol {
	override var section = Section.RDATA
	override var pos = 0
}


class EnumEntry(
	override val scope: Scope,
	override val name: Name,
	val valueNode: AstNode?
) : AstNode(), Symbol {
	var value = 0
}



class Enum(
	override val scope: Scope,
	override val name: Name,
	override val thisScope: Scope,
	val entries: ArrayList<EnumEntry>
) : AstNode(), ScopedSymbol



class Proc(
	override val scope     : Scope,
	override val name      : Name,
	override val thisScope : Scope,
): AstNode(), ScopedSymbol, PosSymbol {
	override var pos = 0
	override var section = Section.TEXT
	var size = 0 // Set by the Assembler
}

class Namespace(
	override val scope     : Scope,
	override val name      : Name,
	override val thisScope : Scope
) : AstNode(), ScopedSymbol

class Label(
	override val scope: Scope,
	override val name: Name
) : AstNode(), PosSymbol {
	override var pos = 0
	override var section = Section.TEXT
}

class RegNode(val value: Reg) : AstNode()

class StringNode(val value: String) : AstNode()

class FloatNode(val value: Double) : AstNode()

class IntNode(val value: Long) : AstNode()

class UnaryNode(val op: UnaryOp, val node: AstNode) : AstNode()

class BinaryNode(val op: BinaryOp, val left: AstNode, val right: AstNode) : AstNode()

class NameNode(val value: Name, override var symbol: Symbol? = null) : AstNode(), SymNode

/*class DotNode(val left: SymNode, val right: SymNode) : AstNode(), SymNode {
	override val symbol get() = right.symbol
}

class RefNode(val left: SymNode, val right: NameNode) : AstNode(), SymNode {
	override val symbol get() = right.symbol
}*/



class OpNode(val type: OpType, val width: Width?, val node: AstNode, val reg: Reg) : AstNode() {
	val isMem get() = type == OpType.MEM
	val isImm get() = type == OpType.IMM

	companion object {
		val NULL = OpNode(OpType.NONE, null, NullNode, Reg.NONE)
		fun reg(reg: Reg) = OpNode(reg.type, reg.type.gpWidth, NullNode, reg)
		fun mem(width: Width?, mem: AstNode) = OpNode(OpType.MEM, width, mem, Reg.NONE)
		fun imm(width: Width?, imm: AstNode) = OpNode(OpType.IMM, width, imm, Reg.NONE)
	}
}



class InsNode(
	val mnemonic : Mnemonic,
	val op1      : OpNode,
	val op2      : OpNode,
	val op3      : OpNode,
	val op4      : OpNode
) : AstNode() {

	val size = when {
		op1 == OpNode.NULL -> 0
		op2 == OpNode.NULL -> 1
		op3 == OpNode.NULL -> 2
		op4 == OpNode.NULL -> 3
		else               -> 4
	}

	val r1 get() = op1.reg
	val r2 get() = op2.reg
	val r3 get() = op3.reg
	val r4 get() = op4.reg

	fun high() = op1.reg.high or op2.reg.high or op3.reg.high or op4.reg.high

}

/*
Helper functions
 */



inline fun UnaryNode.calculate(validity: Boolean, function: (AstNode, Boolean) -> Long): Long = op.calculate(
	function(node, validity && (op == UnaryOp.POS))
)



inline fun BinaryNode.calculate(validity: Boolean, function: (AstNode, Boolean) -> Long): Long = op.calculate(
	function(left, validity && (op == BinaryOp.ADD || op == BinaryOp.SUB)),
	function(right, validity && (op == BinaryOp.ADD))
)