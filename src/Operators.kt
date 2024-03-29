package eyre



enum class BinOp(val precedence: Int, val string: String?) {

	ARR     (11, null),
	DOT     (11, "."),
	CALL    (10, null),
	REF     (10, "::"),
	MUL     (9, "*"),
	DIV     (9, "/"),
	ADD     (8, "+"),
	SUB     (8, "-"),
	SHL     (7, "<<"),
	SHR     (7, ">>"),
	TO      (6, ".."),
	UNTIL   (6, "..<"),
	GT      (5, ">"),
	LT      (5, "<"),
	GTE     (5, ">="),
	LTE     (5, "<="),
	EQ      (4, "=="),
	NEQ     (4, "!="),
	AND     (3, "&"),
	XOR     (3, "^"),
	OR      (3, "|"),
	LAND    (2, "&&"),
	LOR     (2, "||"),
	SET     (1, "="),
	SET_MUL (1, "*="),
	SET_DIV (1, "/="),
	SET_ADD (1, "+="),
	SET_SUB (1, "-="),
	SET_XOR (1, "^="),
	SET_OR  (1, "|="),
	SET_AND (1, "&="),
	SET_SHL (1, "<<="),
	SET_SHR (1, ">>=");

	val isSet = precedence == 1

	fun calc(a: Int, b: Int): Int = when(this) {
		MUL   -> a * b
		DIV   -> a / b
		ADD   -> a + b
		SUB   -> a - b
		SHL   -> a shl b
		SHR   -> a shr b
		GT    -> if(a > b) 1 else 0
		LT    -> if(a < b) 1 else 0
		GTE   -> if(a >= b) 1 else 0
		LTE   -> if(a <= b) 1 else 0
		EQ    -> if(a == b) 1 else 0
		NEQ   -> if(a != b) 1 else 0
		AND   -> a and b
		XOR   -> a xor b
		OR    -> a or b
		LAND  -> if(a != 0 && b != 0) 1 else 0
		LOR   -> if(a == 0 || b == 0) 1 else 0
		else  -> 0
	}

	fun calc(a: Long, b: Long): Long = when(this) {
		MUL   -> a * b
		DIV   -> a / b
		ADD   -> a + b
		SUB   -> a - b
		SHL   -> a shl b.toInt()
		SHR   -> a shr b.toInt()
		GT    -> if(a > b) 1 else 0
		LT    -> if(a < b) 1 else 0
		GTE   -> if(a >= b) 1 else 0
		LTE   -> if(a <= b) 1 else 0
		EQ    -> if(a == b) 1 else 0
		NEQ   -> if(a != b) 1 else 0
		AND   -> a and b
		XOR   -> a xor b
		OR    -> a or b
		LAND  -> if(a != 0L && b != 0L) 1 else 0
		LOR   -> if(a == 0L || b == 0L) 1 else 0
		else  -> 0
	}

}



enum class UnOp(val string: String, val isPostfix: Boolean = false) {

	INC_PRE("++", false),
	INC_POST("++", true),
	DEC_PRE("--", false),
	DEC_POST("--", true),
	POS("+", false),
	NEG("-", false),
	NOT("~", false),
	LNOT("!", false),
	ADDR("&", false),
	DEREF("*", false);

	val precedence = 9

	fun calc(value: Int): Int = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0) 1 else 0
		else -> 0
	}

	fun calc(value: Long): Long = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0L) 1L else 0L
		else -> 0
	}

}