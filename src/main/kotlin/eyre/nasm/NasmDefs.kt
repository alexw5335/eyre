package eyre.nasm



enum class OpEnc(val string: String?) {
	NONE(null),
	ij("ij"),
	N("-"),
	NI("-i"),
	RN("r-"),
	NR("-r"),
	IN("i-"),
	MN("m-"),
	NN("--"),
	RI("ri"),
	MRN("mr-"),
	MR("mr"),
	RM("rm"),
	MI("mi"),
	M("m"),
	R("r"),
	RMI("rmi"),
	I("i"),
	MRI("mri"),
	RVM("rvm"),
	RVMI("rvmi"),
	RVMS("rvms"),
	MVR("mvr"),
	VMI("vmi"),
	RMV("rmv"),
	VM("vm"),
	RMX("rmx"),
	MXR("mxr"),
	MRX("mrx"),
	RMVI("rmvi");
}



enum class ImmType {
	NONE,
	IB,
	IW,
	ID,
	IQ,
	IB_S,
	IB_U,
	ID_S,
	REL,
	REL8;
}



enum class TupleType {
	FV,
	T1S,
	T2,
	T4,
	T8,
	HV,
	HVM,
	T1F64,
	T1F32,
	FVM,
	DUP,
	T1S8,
	T1S16,
	QVM,
	OVM,
	M128;
}



enum class OpPart {
	A64,
	O32,
	O64NW,
	ODF;
}



enum class VSib {
	NONE, VM32X, VM64X, VM64Y, VM32Y, VSIBX, VSIBY, VSIBZ;
}

enum class VexExt {
	E0F, E38, E3A;
}

enum class VexPrefix {
	NP, P66, PF2, PF3;
}

enum class VexL {
	NONE, L128, L256, L512, L0, LZ, L1, LIG;
}

enum class VexW {
	NONE, W0, W1, WIG;
}