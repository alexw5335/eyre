package eyre




interface AstNode {
	val srcPos: SrcPos?
}



interface Symbol {
	val scope: Scope
	val name: Name

	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"
}



interface ScopedSymbol : Symbol {
	val thisScope: Scope
}



data object NullNode : AstNode {
	override val srcPos = null
}



class Namespace(
	override val srcPos    : SrcPos,
	override val scope     : Scope,
	override val name      : Name,
	override val thisScope : Scope
) : AstNode, ScopedSymbol

class Label(
	override val srcPos: SrcPos,
	override val scope: Scope,
	override val name: Name
) : AstNode, Symbol

class IntNode(
	override val srcPos: SrcPos,
	val value: Long
) : AstNode

class UnaryNode(
	override val srcPos: SrcPos,
	val op: UnaryOp,
	val node: AstNode
) : AstNode

class BinaryNode(
	override val srcPos: SrcPos,
	val op: BinaryOp,
	val left: AstNode,
	val right: AstNode
) : AstNode



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