package eyre

enum class BinaryOp(
	val symbol: String?,
	val precedence: Int,
	val calculate: (Long, Long) -> Long
) {

	DOT(".",  5, {_,_->0L}),
	MUL("*",  4, Long::times),
	DIV("/",  4, Long::div),
	ADD("+",  3, Long::plus),
	SUB("-",  3, Long::minus),
	SHL("<<", 2, { a, b -> a shl b.toInt() }),
	SHR(">>", 2, { a, b -> a shr b.toInt() }),
	AND("&",  1, Long::and),
	XOR("^",  1, Long::xor),
	OR( "|",  1, Long::or);

}