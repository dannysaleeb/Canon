Canon {
	var <>seed, <>map, <>voices, <>midiout, <>repeats;

	// what about repeats? repeat the whole canon, yes -- but what about modulating canon?

	// takes a seed Voice, a map CanonMap, and initialises a voices Dict

	*new {
		arg seed=Voice.new(), map=CanonMap.new(), midiout, repeats=1;
		var voices = Dictionary.new();

		map.numVoices.do({ //if no map provided by user, default CanonMap.numVoices is used (4)

			arg i;
			// add numVoices * Voice to voices dictionary, with count i as key;
			// each Voice gets the seed's notepat transposed by corresponding interval given in map (default: 0)
			// each Voice gets the seed's durpat multiplied by corresponding multiplier given in map (default: 1)
			voices.put(i, Voice(seed.notepat + map.intervals.wrapAt(i), seed.durpat * map.dur_multipliers[i]))
		});

		^super.newCopyArgs(seed, map, voices, midiout, repeats) // voices are therefore constructed when the Canon is initialised
	}

	construct_voices {
		this.voices = Dictionary.new();
		// this just resets it and gets rid of all the important information ... needs work
		this.map.numVoices.do({
			arg i;
			this.voices.put(i, Voice(this.seed.notepat + this.map.intervals[i], this.seed.durpat * this.map.dur_multipliers[i]))
		})
	}

	// wonder if there's a quicker and more efficient way to do this?
	set_map_multipliers {
		arg multipliers;

		^Canon.new(this.seed, CanonMap.new(this.map.numVoices, this.map.deltavals, this.map.intervals, multipliers, this.map.midichans, this.map.instruments, this.map.repeats), this.midiout, this.repeats)

	}

	set_map_deltavals {
		arg deltavals;

		^Canon.new(this.seed, CanonMap.new(this.map.numVoices, deltavals, this.map.intervals, this.map.dur_multipliers, this.map.midichans, this.map.instruments, this.map.repeats), this.midiout, this.repeats)

	}

	set_map_intervals {
		arg intervals;

		^Canon.new(this.seed, CanonMap.new(this.map.numVoices, this.map.deltavals, intervals, this.map.dur_multipliers, this.map.midichans, this.map.instruments, this.map.repeats), this.midiout, this.repeats)

	}

	get_pattern {
		var patterns = Array.new();
		var deltas = Array.new();
		// create a Ptpar, first checking if midi or synthesis
		if (
			this.midiout != nil,
			{
				// get midi pattern
				^Ptpar(
					{
						this.map.numVoices.do({
							arg i; // get a list of Pbinds that has voice[i].notemap for \midinotes and voice[i].durpat for \dur
							patterns = patterns.add(
								Pbind(
									// add ability to modulate each time ...
									\type, \midi,
									\midiout, this.midiout,
									\chan, this.map.midichans[i],
									\midicmd, \noteOn,
									\midinote, this.voices[i].notepat,
									\dur, this.voices[i].durpat,
									\legato, 1,
									\amp, 0.25
									// add additional paramaters as necessary
								)
							);
							// get a list of delta vals with map.deltavals[i]
							deltas = deltas.add(this.map.deltavals[i]);
						});
						[deltas, patterns].lace // zip the two lists together, deltas first
					}.value,
					this.repeats // number of times the canon plays through
				).asStream
			},
			{
				// get non-midi pattern
				^Ptpar(
					{
						this.map.numVoices.do({
							arg i; // get a list of Pbinds that has voice[i].notemap for \midinotes and voice[i].durpat for \dur
							patterns = patterns.add(
								Pbind(
									// add ability to modulate each time ...
									\midinote, this.voices[i].notepat,
									\dur, this.voices[i].durpat,
									\instrument, this.map.instruments[i]
									// add additional paramaters as necessary
								)
							);
							// get a list of delta vals with map.deltavals[i]
							deltas = deltas.add(this.map.deltavals[i]);
						});
						[deltas, patterns].lace // zip the two lists together, deltas first
					}.value,
					this.repeats // number of times the canon plays through
				).asStream
			}
		)
	}

	get_stream {
		^this.get_pattern.asStream
	}

	// could add a flag to map to indicate midi or not ... in which case get_stream produces midi stream

	play {
		arg tempo=60, instrument;
		// this will simply play the stream at given bpm (default 60bpm)
		this.get_stream.asEventStreamPlayer.play(TempoClock(tempo / 60))
	}

}

Voice {
	// holds only notes and corresponding durs
	var <>notepat, <>durpat;

	*new {
		arg notepat, durpat;

		notepat = notepat ?

		Pseq([
			// tallis' canon
			60, 60, 59, 60,
			60, 62, 62, 64,
			60, 65, 65, 64,
			64, 62, 62, 60,
			67, 65, 62, 64,
			64, 62, 62, 60,
			55, 57, 59, 60,
			64, 62, 62, 60
		]);

		durpat = durpat ? 1;

		^super.newCopyArgs(notepat, durpat)
	}
}

CanonMap {
	// for use with CanonNew : holds everything except notes and durations
	var <>numVoices, <>deltavals, <>intervals, <>dur_multipliers, <>midichans, <>instruments, <>repeats;

	*new {
		arg numVoices=4, deltavals, intervals, dur_multipliers, midichans, instruments, repeats;

		// there must be an easier way to do this...

		deltavals = deltavals ? numVoices.collect({arg i; i * 4});
		intervals = intervals ? Array.fill(numVoices, 0);
		dur_multipliers = dur_multipliers ? Array.fill(numVoices, 1);
		midichans = midichans ? numVoices.collect({arg i; i});
		instruments = instruments ? Array.fill(numVoices, \default);
		repeats = repeats ? Array.fill(numVoices, 1);

		// make sure deltavals is the right size
		if (
			deltavals.size != numVoices,
			{
				"deltavals arg does not match number of voices: wrapping or reducing  values".warn;
				deltavals = numVoices.collect({arg i; deltavals.wrapAt(i)})
			}
		);
		// make sure intervals is the right size
		if (
			intervals.size != numVoices,
			{
				"intervals arg does not match number of voices: wrapping or reducing  values".warn;
				intervals = numVoices.collect({arg i; intervals.wrapAt(i)})
			}
		);
		// make sure dur_multipliers is the right size
		if (
			dur_multipliers.size != numVoices,
			{
				"dur_multipliers arg does not match number of voices: wrapping or reducing  values".warn;
				dur_multipliers = numVoices.collect({arg i; dur_multipliers.wrapAt(i)})
			}
		);
		// make sure midichans is the right size
		if (
			midichans.size != numVoices,
			{
				"midichans arg does not match number of voices: wrapping or reducing  values".warn;
				midichans = numVoices.collect({arg i; midichans.wrapAt(i)})
			}
		);
		// make sure instruments is the right size
		if (
			instruments.size != numVoices,
			{
				"instruments arg does not match number of voices: wrapping or reducing  values".warn;
				instruments = numVoices.collect({arg i; instruments.wrapAt(i)})
			}
		);
		// make sure repeats is the right size
		if (
			repeats.size != numVoices,
			{
				"repeats arg does not match number of voices: wrapping or reducing values".warn;
				repeats = numVoices.collect({arg i; repeats.wrapAt(i)})
			}
		);



		^super.newCopyArgs(numVoices, deltavals, intervals, dur_multipliers, midichans, instruments, repeats)
	}
}