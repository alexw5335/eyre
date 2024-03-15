package eyre



enum class BinOp(val precedence: Int, val string: String?) {

	CALL  (12, null),
	ARR   (11, null),
	DOT   (11, "."),
	REF   (10, "::"),
	MUL   (9, "*"),
	DIV   (9, "/"),
	ADD   (8, "+"),
	SUB   (8, "-"),
	SHL   (7, "<<"),
	SHR   (7, ">>"),
	SAR   (7, ">>>"),
	TO    (6, ".."),
	UNTIL (6, "..<"),
	GT    (5, ">"),
	LT    (5, "<"),
	GTE   (5, ">="),
	LTE   (5, "<="),
	EQ    (4, "=="),
	NEQ   (4, "!="),
	AND   (3, "&"),
	XOR   (3, "^"),
	OR    (3, "|"),
	LAND  (2, "&&"),
	LOR   (2, "||"),
	SET   (1, "=");

	val leftRegValid get() = this == ADD || this == SUB
	val rightRegValid get() = this == ADD

	fun calc(a: Int, b: Int): Int = when(this) {
		TO    -> 0
		UNTIL -> 0
		CALL   -> 0
		ARR   -> 0
		DOT   -> 0
		REF   -> 0
		MUL   -> a * b
		DIV   -> a / b
		ADD   -> a + b
		SUB   -> a - b
		SHL   -> a shl b
		SHR   -> a shr b
		SAR   -> a ushr b
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
		SET   -> 0
	}

	fun calc(a: Long, b: Long): Long = when(this) {
		TO    -> 0
		UNTIL -> 0
		CALL   -> 0
		ARR   -> 0
		DOT   -> 0
		REF   -> 0
		MUL   -> a * b
		DIV   -> a / b
		ADD   -> a + b
		SUB   -> a - b
		SHL   -> a shl b.toInt()
		SHR   -> a shr b.toInt()
		SAR   -> a ushr b.toInt()
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
		SET   -> 0
	}

}



enum class UnOp(val string: String) {

	POS("+"),
	NEG("-",),
	NOT("~"),
	LNOT("!"),
	ADDR("&"),
	DEREF("*");

	val precedence = 9
	val regValid get() = this == POS

	fun calc(value: Int): Int = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0) 1 else 0
		ADDR -> 0
		DEREF -> 0
	}

	fun calc(value: Long): Long = when(this) {
		POS  -> value
		NEG  -> -value
		NOT  -> value.inv()
		LNOT -> if(value == 0L) 1L else 0L
		ADDR -> 0
		DEREF -> 0
	}

}