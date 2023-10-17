package eyre.gen

import eyre.Width.*
import eyre.Width

enum class ManualOp {
	R8,
	R16,
	R32,
	R64,
	M8,
	M16,
	M32,
	M64,
	M128,
	M256,
	M512,
	MEM,
	X,
	Y,
	DX,
	CL,
	AL,
	AX,
	EAX,
	RAX,
	I8,
	I16,
	I32,
	I64,
	REL8,
	REL32,

	// Width multi ops
	A,
	R,
	M,
	I,
	O,

	// Multi ops
	E,
	EM,
	S,
	SM,
	RM,
	XM,
	YM;

	fun ofWidth(width: Width) = when(this) {
		A -> when(width) { BYTE -> AL; WORD -> AX; DWORD -> EAX; QWORD -> RAX; else -> error("Invalid width") }
		O, R -> when(width) { BYTE -> R8; WORD -> R16; DWORD -> R32; QWORD -> R64; else -> error("Invalid width") }
		M -> when(width) { BYTE -> M8; WORD -> M16; DWORD -> M32; QWORD -> M64; else -> error("Invalid width") }
		I -> when(width) { BYTE -> I8; WORD -> I16; DWORD -> I32; QWORD -> I32; else -> error("Invalid width") }
		else -> error("Invalid op")
	}

}