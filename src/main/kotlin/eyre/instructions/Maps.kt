package eyre.instructions

object Maps {

	val opSizes = OpSize.values().associateBy { it.name }
	val arches = Arch.values().associateBy { it.name.trimStart('_') }
	val extensions = Extension.values().associateBy { it.name.trimStart('_') }
	val opParts = OpPart.values().associateBy { it.name.lowercase().replace('_', ',') }
	val immWidths = ImmWidth.values().associateBy { it.name.lowercase().replace('_', ',') }
	val vsibParts = VsibPart.values().associateBy { it.name.lowercase() }
	val sizeMatches = SizeMatch.values().associateBy { it.name }
	val argMatches = ArgMatch.values().associateBy { it.name }
	val operands = Operand.values().associateBy { it.string }

	val widthArray = Width.values()
	val operandsArray = Operands.values()

	val smOperands = HashMap<String, Pair<Operands, Width>>().also { map ->
		for(operands in operandsArray)
			for((i, s) in operands.smStrings?.withIndex() ?: continue)
				s?.let { map[it] = operands to widthArray[i] }
	}

	val explicitOperands = HashMap<String, Pair<Operands, Width>>().also { map ->
		for(operands in operandsArray)
			for((i, s) in operands.strings?.withIndex() ?: continue)
				s?.let { map[it] = operands to widthArray[i] }
		for(operands in operandsArray)
			for((i, s) in operands.sStrings?.withIndex() ?: continue)
				s?.let { map[it] = operands to widthArray[i + Width.XWORD.ordinal] }
	}



	val customMnemonics = setOf(
		"ENTER",
		"Jcc",
		"JMP",
		"CALL",
		"LOOP",
		"LOOPE",
		"LOOPNE",
		"LOOPZ",
		"LOOPNZ",
		"MOV",
		"PUSH",
		"XCHG",
		"POP",
		"IN",
		"OUT"
	)

	val ignoredParts = setOf(
		"hle",
		"nof3",
		"hlenl",
		"hlexr",
		"adf",
		"norexb",
		"norexx",
		"norexr",
		"norexw",
		"nohi",
		"nof3",
		"norep",
		"repe",
		"np",
		"iwdq"
	)


	val invalidExtras = setOf(
		"NOLONG",
		"NEVER",
		"UNDOC",
		"OBSOLETE",
		"AMD",
		"CYRIX",
		"LATEVEX",
		"OPT"
	)

	val ignoredExtras = setOf(
		"DEFAULT",
		"ANY",
		"VEX",
		"EVEX",
		"NOP",
		"HLE",
		"NOHLE",
		"PRIV",
		"SMM",
		"PROT",
		"LOCK",
		"LONG",
		"BND",
		"MIB",
		"SIB",
		"SIZE",
		"ANYSIZE",
		"ND"
	)

}