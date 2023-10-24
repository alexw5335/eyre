package eyre.gen

enum class SimdCombo(val first: String, val second: String) {
	E_I8("MM_I8", "X_I8"),
	E_EM("MM_MMM64", "X_XM128"),
	S_S_SM("X_X_XM128", "Y_Y_YM256"),
	S_S_M("X_X_M128", "Y_Y_M256"),
	M_S_S("M128_X_X", "M256_Y_Y"),
	S_SM_I8("X_XM128_I8", "Y_YM256_I8"),
	S_S_SM_S("X_X_XM128_X", "Y_Y_YM256_Y"),
	S_SM("X_XM128", "Y_YM256"),
	SM_S("XM128_X", "YM256_Y"),
	M_S("M128_X", "M256_Y"),
	S_M("X_M128", "Y_M256"),
	R32_S("R32_X", "R32_Y"),
	R64_S("R64_X", "R64_Y"),
	S_S_I8("X_X_I8", "Y_Y_I8"),
	S_S_XM128("X_X_XM128", "Y_Y_XM128"),
	S_S_SM_I8("X_X_XM128_I8", "Y_Y_YM256_I8");

	val isSse get() = this == E_EM || this == E_I8 // MM, X  prefix=NONE for MM, prefix=66 for X
	val isAvx get() = !isSse // X, Y   VEX.L=0 for X, VEX.L=1 for Y

}