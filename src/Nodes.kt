package eyre

abstract class Node {
	var srcPos: SrcPos? = null
}

class NameNode(val value: Name) : Node()

class IntNode(val value: Int) : Node()

class StringNode(val value: String) : Node()

class UnNode(val op: UnOp, val child: Node) : Node()

class BinNode(val op: BinOp, val left: Node, val right: Node) : Node()