// audio.js - Web Audio API Sound Synthesizer for Talking Tom 2
// 100% Procedural Audio - No external files needed, works offline!

class TomAudioEngine {
    constructor() {
        this.ctx = null;
        this.isMuted = false;
        this.purrOsc = null;
        this.purrGain = null;
        this.isPurring = false;
    }

    init() {
        if (!this.ctx) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            this.ctx = new AudioContext();
        }
        if (this.ctx && this.ctx.state === 'suspended') {
            this.ctx.resume();
        }
    }

    toggleMute() {
        this.isMuted = !this.isMuted;
        if (this.isMuted && this.isPurring) {
            this.stopPurr();
        }
        return this.isMuted;
    }

    // 1. Cat Meow Sound
    playMeow(pitch = 1.0) {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;
        
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        const filter = this.ctx.createBiquadFilter();

        osc.type = 'triangle';
        filter.type = 'bandpass';
        filter.frequency.setValueAtTime(800 * pitch, now);
        filter.Q.value = 3;

        // Cat pitch contour: Start mid -> rise high -> fall gently
        osc.frequency.setValueAtTime(360 * pitch, now);
        osc.frequency.exponentialRampToValueAtTime(580 * pitch, now + 0.15);
        osc.frequency.exponentialRampToValueAtTime(320 * pitch, now + 0.45);

        gain.gain.setValueAtTime(0.001, now);
        gain.gain.linearRampToValueAtTime(0.35, now + 0.08);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.5);

        osc.connect(filter);
        filter.connect(gain);
        gain.connect(this.ctx.destination);

        osc.start(now);
        osc.stop(now + 0.52);
    }

    // 2. Continuous Purring Sound (when petted)
    startPurr() {
        if (this.isMuted || this.isPurring) return;
        this.init();
        const now = this.ctx.currentTime;

        const osc = this.ctx.createOscillator();
        const lfo = this.ctx.createOscillator();
        const lfoGain = this.ctx.createGain();
        const mainGain = this.ctx.createGain();
        const filter = this.ctx.createBiquadFilter();

        osc.type = 'sawtooth';
        osc.frequency.value = 42; // Deep rumble

        // LFO for purr pulsation (~18 Hz cat purr vibration)
        lfo.type = 'sine';
        lfo.frequency.value = 16;
        lfoGain.gain.value = 0.2;

        lfo.connect(mainGain.gain);
        lfo.start();

        filter.type = 'lowpass';
        filter.frequency.value = 120;

        mainGain.gain.setValueAtTime(0.18, now);

        osc.connect(filter);
        filter.connect(mainGain);
        mainGain.connect(this.ctx.destination);

        osc.start(now);

        this.purrOsc = osc;
        this.purrLfo = lfo;
        this.purrGain = mainGain;
        this.isPurring = true;
    }

    stopPurr() {
        if (!this.isPurring) return;
        try {
            if (this.purrGain) {
                this.purrGain.gain.linearRampToValueAtTime(0.001, this.ctx.currentTime + 0.1);
            }
            setTimeout(() => {
                if (this.purrOsc) this.purrOsc.stop();
                if (this.purrLfo) this.purrLfo.stop();
                this.purrOsc = null;
                this.purrLfo = null;
                this.isPurring = false;
            }, 120);
        } catch (e) {
            this.isPurring = false;
        }
    }

    // 3. Poke / Slap / Ouch Sound
    playSlap() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        // Snappy pop
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'square';
        osc.frequency.setValueAtTime(320, now);
        osc.frequency.exponentialRampToValueAtTime(80, now + 0.12);

        gain.gain.setValueAtTime(0.4, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.14);

        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(now);
        osc.stop(now + 0.15);

        // Followed by high squeaky "Eek!"
        setTimeout(() => {
            if (this.isMuted) return;
            const squeak = this.ctx.createOscillator();
            const sqGain = this.ctx.createGain();
            squeak.type = 'sine';
            squeak.frequency.setValueAtTime(650, this.ctx.currentTime);
            squeak.frequency.exponentialRampToValueAtTime(400, this.ctx.currentTime + 0.2);

            sqGain.gain.setValueAtTime(0.3, this.ctx.currentTime);
            sqGain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.2);

            squeak.connect(sqGain);
            sqGain.connect(this.ctx.destination);
            squeak.start(this.ctx.currentTime);
            squeak.stop(this.ctx.currentTime + 0.22);
        }, 50);
    }

    // 4. Tickle / Giggle Sound
    playGiggle() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;
        const freqs = [600, 750, 900, 720, 850];

        freqs.forEach((f, i) => {
            const osc = this.ctx.createOscillator();
            const gain = this.ctx.createGain();
            const t = now + i * 0.08;

            osc.type = 'sine';
            osc.frequency.setValueAtTime(f, t);
            osc.frequency.linearRampToValueAtTime(f + 80, t + 0.06);

            gain.gain.setValueAtTime(0.001, t);
            gain.gain.linearRampToValueAtTime(0.25, t + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.001, t + 0.07);

            osc.connect(gain);
            gain.connect(this.ctx.destination);
            osc.start(t);
            osc.stop(t + 0.08);
        });
    }

    // 5. Eating Munch / Crunch
    playMunch() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        // Noise burst for crunchy chew
        const bufferSize = this.ctx.sampleRate * 0.08;
        const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < bufferSize; i++) {
            data[i] = (Math.random() * 2 - 1) * 0.5;
        }

        const noise = this.ctx.createBufferSource();
        noise.buffer = buffer;

        const filter = this.ctx.createBiquadFilter();
        filter.type = 'bandpass';
        filter.frequency.value = 1200 + Math.random() * 400;

        const gain = this.ctx.createGain();
        gain.gain.setValueAtTime(0.35, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.08);

        noise.connect(filter);
        filter.connect(gain);
        gain.connect(this.ctx.destination);

        noise.start(now);
    }

    // 6. Gulp & Happy Burp
    playGulpAndBurp() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        // Gulp
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(320, now);
        osc.frequency.exponentialRampToValueAtTime(140, now + 0.18);

        gain.gain.setValueAtTime(0.35, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.2);

        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(now);
        osc.stop(now + 0.22);

        // Funny small burp after 0.3s
        setTimeout(() => {
            if (this.isMuted) return;
            const bOsc = this.ctx.createOscillator();
            const bGain = this.ctx.createGain();
            const t = this.ctx.currentTime;

            bOsc.type = 'sawtooth';
            bOsc.frequency.setValueAtTime(110, t);
            bOsc.frequency.linearRampToValueAtTime(80, t + 0.25);

            bGain.gain.setValueAtTime(0.3, t);
            bGain.gain.exponentialRampToValueAtTime(0.001, t + 0.28);

            bOsc.connect(bGain);
            bGain.connect(this.ctx.destination);
            bOsc.start(t);
            bOsc.stop(t + 0.3);
        }, 320);
    }

    // 7. Chili Fire Breath
    playFireBreath() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        // Roaring flame whoosh
        const bufferSize = this.ctx.sampleRate * 0.8;
        const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < bufferSize; i++) {
            data[i] = Math.random() * 2 - 1;
        }

        const noise = this.ctx.createBufferSource();
        noise.buffer = buffer;

        const filter = this.ctx.createBiquadFilter();
        filter.type = 'lowpass';
        filter.frequency.setValueAtTime(300, now);
        filter.frequency.linearRampToValueAtTime(2200, now + 0.3);
        filter.frequency.linearRampToValueAtTime(400, now + 0.8);

        const gain = this.ctx.createGain();
        gain.gain.setValueAtTime(0.001, now);
        gain.gain.linearRampToValueAtTime(0.45, now + 0.2);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.8);

        noise.connect(filter);
        filter.connect(gain);
        gain.connect(this.ctx.destination);

        noise.start(now);
    }

    // 8. Fart & Toilet Flush
    playFart() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(95, now);
        osc.frequency.linearRampToValueAtTime(60, now + 0.3);

        // Vibrato for flutter
        const vibrato = this.ctx.createOscillator();
        const vGain = this.ctx.createGain();
        vibrato.frequency.value = 24;
        vGain.gain.value = 20;
        vibrato.connect(osc.frequency);
        vibrato.start(now);

        gain.gain.setValueAtTime(0.45, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(now);
        osc.stop(now + 0.38);
        vibrato.stop(now + 0.38);
    }

    playFlush() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        // Water swirl rush
        const bufferSize = this.ctx.sampleRate * 1.2;
        const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < bufferSize; i++) {
            data[i] = Math.random() * 2 - 1;
        }

        const noise = this.ctx.createBufferSource();
        noise.buffer = buffer;

        const filter = this.ctx.createBiquadFilter();
        filter.type = 'bandpass';
        filter.frequency.setValueAtTime(1400, now);
        filter.frequency.exponentialRampToValueAtTime(350, now + 1.1);

        const gain = this.ctx.createGain();
        gain.gain.setValueAtTime(0.4, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 1.2);

        noise.connect(filter);
        filter.connect(gain);
        gain.connect(this.ctx.destination);

        noise.start(now);
    }

    // 9. Soap Bubble Pop
    playBubblePop() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(500 + Math.random() * 300, now);
        osc.frequency.exponentialRampToValueAtTime(1100 + Math.random() * 400, now + 0.07);

        gain.gain.setValueAtTime(0.3, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.08);

        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(now);
        osc.stop(now + 0.09);
    }

    // 10. Shower Water Spray
    playWaterSpray() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        const bufferSize = this.ctx.sampleRate * 0.3;
        const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < bufferSize; i++) {
            data[i] = Math.random() * 2 - 1;
        }

        const noise = this.ctx.createBufferSource();
        noise.buffer = buffer;

        const filter = this.ctx.createBiquadFilter();
        filter.type = 'highpass';
        filter.frequency.value = 2400;

        const gain = this.ctx.createGain();
        gain.gain.setValueAtTime(0.2, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);

        noise.connect(filter);
        filter.connect(gain);
        gain.connect(this.ctx.destination);

        noise.start(now);
    }

    // 11. Snore Sound (Bedroom sleep)
    playSnore() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        // Inhale snore
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        const filter = this.ctx.createBiquadFilter();

        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(65, now);
        osc.frequency.linearRampToValueAtTime(95, now + 0.4);

        filter.type = 'bandpass';
        filter.frequency.value = 400;

        gain.gain.setValueAtTime(0.001, now);
        gain.gain.linearRampToValueAtTime(0.2, now + 0.2);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.5);

        osc.connect(filter);
        filter.connect(gain);
        gain.connect(this.ctx.destination);

        osc.start(now);
        osc.stop(now + 0.55);
    }

    // 12. Medical Healing Sparkle
    playCureSparkle() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;
        const notes = [523.25, 659.25, 783.99, 1046.5, 1318.5]; // C E G C E

        notes.forEach((freq, idx) => {
            const osc = this.ctx.createOscillator();
            const gain = this.ctx.createGain();
            const t = now + idx * 0.08;

            osc.type = 'sine';
            osc.frequency.setValueAtTime(freq, t);

            gain.gain.setValueAtTime(0.001, t);
            gain.gain.linearRampToValueAtTime(0.25, t + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.001, t + 0.2);

            osc.connect(gain);
            gain.connect(this.ctx.destination);
            osc.start(t);
            osc.stop(t + 0.22);
        });
    }

    // 13. Coin Jingle
    playCoin() {
        if (this.isMuted) return;
        this.init();
        const now = this.ctx.currentTime;

        const osc1 = this.ctx.createOscillator();
        const gain1 = this.ctx.createGain();
        osc1.type = 'sine';
        osc1.frequency.setValueAtTime(987.77, now); // B5

        gain1.gain.setValueAtTime(0.25, now);
        gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.1);

        osc1.connect(gain1);
        gain1.connect(this.ctx.destination);
        osc1.start(now);
        osc1.stop(now + 0.1);

        const osc2 = this.ctx.createOscillator();
        const gain2 = this.ctx.createGain();
        osc2.type = 'sine';
        osc2.frequency.setValueAtTime(1318.51, now + 0.08); // E6

        gain2.gain.setValueAtTime(0.28, now + 0.08);
        gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

        osc2.connect(gain2);
        gain2.connect(this.ctx.destination);
        osc2.start(now + 0.08);
        osc2.stop(now + 0.38);
    }

    // 14. High-Pitched Helium Talking Voice (Tom Voice Synthesizer)
    speakTomVoice(text, onStart, onEnd) {
        if (this.isMuted) {
            if (onEnd) onEnd();
            return;
        }

        if ('speechSynthesis' in window) {
            window.speechSynthesis.cancel();
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.pitch = 1.95; // Super high-pitch Tom cat voice
            utterance.rate = 1.25;  // Energetic fast speech
            utterance.volume = 1.0;

            utterance.onstart = () => {
                if (onStart) onStart();
            };
            utterance.onend = () => {
                if (onEnd) onEnd();
            };
            utterance.onerror = () => {
                if (onEnd) onEnd();
            };

            // Pick child/female/Hindi voice if available
            const voices = window.speechSynthesis.getVoices();
            const childOrFemaleVoice = voices.find(v => v.name.includes('Child') || v.name.includes('Female') || v.lang.startsWith('hi') || v.lang.startsWith('en'));
            if (childOrFemaleVoice) {
                utterance.voice = childOrFemaleVoice;
            }

            window.speechSynthesis.speak(utterance);
        } else {
            // Fallback funny chirp sequence
            this.playGiggle();
            if (onStart) onStart();
            setTimeout(() => {
                if (onEnd) onEnd();
            }, 800);
        }
    }
}

// Global instance
window.tomAudio = new TomAudioEngine();
