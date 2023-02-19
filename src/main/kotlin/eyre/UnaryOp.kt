package eyre

enum class UnaryOp(val symbol: String, val calculate: (Long) -> Long) {

	POS("+", { it }),
	NEG("-", { -it }),
	NOT("~", { it.inv() });

}