// uses extensions to Stream eventsArray and transpose.

// Canon takes an event stream as seed, and dictionary of Voices as voices
CanonOld {
	var <seed, <voices;

	*new {
		arg seed, voices;

		^super.newCopyArgs(seed, voices)
	}

	play_seed {
		arg t=TempoClock(1), interval=0, instrument=\default;

		this.seed.asStream.transpose(interval, instrument).asEventStreamPlayer.play(t)
	}

	// the way I've written this means difficult to turn into midi file, need to change to produce stream, which can be played as a whole, rather than playing the seed repeatedly ... for now get midi via Logic Pro
	play_seed_midi {
		arg t=TempoClock(1), interval=0, midiout, chan;
		var temp_arr;

		// convert to Array
		temp_arr = this.seed.asStream.eventsArray.copy;

		// add important info to every event in arr
		temp_arr.do({
			arg event;
			event.type = \midi;
			event.midiout = midiout;
			event.chan = chan;
			event.midicmd = \noteOn;
		});

		// convert back to stream
		Routine({
			temp_arr.do({
				arg event;
				event.yield
			})
		}).asStream
		.transpose(interval)
		.asEventStreamPlayer
		.play(t)
	}

	// rather than playing the seed each time, I need to build a stream, and I also need to be able to get a stream for each individual voice.

	play {
		arg tempo, instrument;
		var clock = TempoClock(tempo / 60);
		this.voices.do({
			arg voice;
			clock.sched(voice.delta, {this.play_seed(t: clock, interval: voice.interval, instrument: instrument)}
		)})
	}

	play_midi {
		arg tempo, midiout;
		var clock = TempoClock(tempo / 60);
		this.voices.do({
			arg voice;
			clock.sched(voice.delta, {this.play_seed_midi(t: clock, interval: voice.interval, midiout: midiout, chan: voice.midichan)}
		)})
	}

	get_stream {
		// implement
	}

	get_midistream {
		// implement
	}
}

VoiceOld {
	// delta is secs; interval is semitones; multiplier is optional
	var <>delta, <>interval, <>dur_multiplier, <>midichan;

	*new {
		arg delta=2, interval=5, dur_multiplier=1, midichan=0;

		^super.newCopyArgs(delta, interval, dur_multiplier, midichan)
	}
}

// Going forward I would like to be able to transpose the seed on each playthrough ... how to do that?! So it modulates ...


