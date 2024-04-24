package eyre

enum class GenType {


	/**
	 * Placeholder type, always produces an error.
	 */
	NONE,



	// ARRAYS



	/**
	 * Potential leaf, resolves to single memory operand
	 */
	ARRAY_LEAF_CONST,

	/**
	 * Non-leaf, minimum 1 register
	 */
	ARRAY_EXPR_CONST,

	/**
	 * Non-leaf, minimum 2 registers.
	 */
	ARRAY_LEAF_EXPR,

	/**
	 * Non-leaf, minimum 2 registers
	 */
	ARRAY_EXPR_EXPR,



	// TODO: Organise

	/** Immediate operand, always leaf */
	I32,
	/** Register operand <- qword or unsigned dword immediate, initialised with `REX.W B8+r MOV R64, I64`. */
	I64,
	/** Memory operand, potential leaf */
	SYM,
	STRING,
	MEMBER,
	/** Pointer dereference, non-leaf */
	DEREF,
	/** Nodes with children, non-leaf */
	UNARY_LEAF,
	UNARY_NODE,
	BINARY_NODE_NODE_LEFT,
	BINARY_NODE_NODE_RIGHT,
	BINARY_NODE_LEAF,
	BINARY_LEAF_NODE,
	BINARY_LEAF_LEAF,
	BINARY_LEAF_NODE_COMMUTATIVE,
	/** Always leaf */
	BINARY_LEAF_LEAF_COMMUTATIVE;
}