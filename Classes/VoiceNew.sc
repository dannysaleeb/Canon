VoiceNew {

	// Primed for use with rhythm trees, as the

	var <notes, <durations;

	*new {
		arg notes, durations = 1;

		notes = notes ?

		[// tallis' canon
			0, 0, -1, 0,
			0, 1, 1, 2,
			0, 3, 3, 2,
			2, 1, 1, 0,
			4, 3, 1, 2,
			2, 1, 1, 0,
			-3, -2, -1, 0,
			2, 1, 1, 0
		];

		if (
			durations.isKindOf(Number),
			{
				durations = Array.fill(notes.size, durations)
			},
			{
				if (
					durations.size != notes.size,
					{
						durations = notes.size.collect({
							arg i;
							durations.wrapAt(1)
						})
					}
				)
			}
		);
		// make sure both are Pseqs
		notes = Pseq(notes, 1);
		durations = Pseq(durations, 1);

		^super.newCopyArgs(notes, durations)
	}

	rt_divide {
		arg divisions, probability, prob_change_factor, depth;
		var rt = Array.new(), new_durs;

		// generate rt
		rt = notes.size.ds_rtgenerate(divisions, probability, prob_change_factor, depth);

		^rt;

		/*new_durs = durations.collect({
			arg duration;
			duration.ds_rtdivide(divisions, probability, prob_change_factor, depth)
		});

		^VoiceNew(notepat, )*/
	}

}

// I think I want to be able to set durations when I use rhythm tree divide on it ... in that case, I'll need to change both notes and durations to match, so for each

// to make that work, I need to make an rt, separate from notes and durations ... that will be the basis for converting to pattern in both cases, and I should return a new Voice... I think ...