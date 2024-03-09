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

			// the problem is in the creation of each Voice I'm trying to make a Pseq, but not feeding it a collection ...
			voices.put(i, Voice(seed.notepat.asStream.eventsArray + map.intervals.wrapAt(i), seed.durpat.asStream.eventsArray * map.dur_multipliers[i]));
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
		arg clock=TempoClock(1), instrument, quant=Quant(0);
		// this will simply play the stream at given bpm (default 60bpm)
		this.get_stream.asEventStreamPlayer.play(clock, quant: quant)
	}

	get_midi {
		arg filepath, tempo=60, timeSignature="4/4";
		var file, pattern, sorted_voices=[];

		file = SimpleMIDIFile(filepath);
		file.init1(this.voices.size + 1, tempo, timeSignature);

		file.timeMode = \seconds;

		this.voices.size.do({
			arg i;
			sorted_voices = sorted_voices.add(this.voices[i]);
		});

		// voices is a dict, and not sorted ...
		sorted_voices.do({
			arg voice, track;
			var notes, durs, onsets=[map.deltavals[track]];
			// get notes
			notes = voice.notepat.asStream.eventsArray;
			// get durs
			if (
				voice.durpat.isKindOf(Number),
				{
					durs = notes.size.collect({
						voice.durpat;
					})
				},
				{
					durs = voice.durpat.asStream.eventsArray;
				}
			);
			// get onsets
			durs.do({
				arg dur, i;
				onsets = onsets.add(onsets[i] + dur);
			});
			// remove the last onset (which is end of final note)

			notes.postln;
			durs.postln;
			onsets.postln;

			notes.size.do({
				arg i;
				file.addNote(notes[i], 64, onsets[i], durs[i], track: track + 1)
			})
		});

		// how to get onsets...

		// actually create each Pbind individually from voices.notepat and voices.durpat...
		/*file.adjustEndOfTrack;*/
		file.write
	}

}

Voice {
	// holds only notes and corresponding durs
	var <>notepat, <>durpat;

	*new {
		arg notepat, durpat=[1];

		// make sure they match first
		if (
			durpat.size != notepat.size,
			{
				durpat = notepat.size.collect({
					arg i;
					durpat.wrapAt(i)
				});
			}
		);

		durpat = Pseq(durpat);

		notepat = Pseq(notepat) ?

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

		// need durpat to correspond with notepat ...
		// change Voice to handle array rather than pat?

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