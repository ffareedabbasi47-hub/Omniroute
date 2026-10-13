// ==========================================================================
// Talking Tom Audio Engine - 100% Procedural & Real Voice Pitch-Shifting
// ==========================================================================

class TomAudio {
  constructor() {
    this.ctx = null;
    this.purrOsc = null;
    this.purrGain = null;
    this.isRecording = false;
    this.mediaRecorder = null;
    this.audioChunks = [];
    this.currentPlayingSource = null;
    this.analyser = null;
    this.dataArray = null;
    this.onAudioVolumeCallback = null;
  }

  init() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      this.ctx = new AudioCtx();
      this.analyser = this.ctx.createAnalyser();
      this.analyser.fftSize = 64;
      this.dataArray = new Uint8Array(this.analyser.frequencyBinCount);
    }
    if (this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  // --- 1. Procedural Cat Sounds ---

  // Meow Sound
  playMeow(pitch = 1.0) {
    this.init();
    const t = this.ctx.currentTime;

    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    const filter = this.ctx.createBiquadFilter();

    osc.type = 'triangle';
    filter.type = 'bandpass';
    filter.Q.value = 3.5;

    // Pitch envelope: starts medium, rises, slides down (Meeeee-oww)
    const baseFreq = 380 * pitch;
    osc.frequency.setValueAtTime(baseFreq, t);
    osc.frequency.linearRampToValueAtTime(baseFreq * 1.5, t + 0.15);
    osc.frequency.exponentialRampToValueAtTime(baseFreq * 0.75, t + 0.55);

    // Filter sweep gives vocal formant "me-ow"
    filter.frequency.setValueAtTime(600, t);
    filter.frequency.linearRampToValueAtTime(1400, t + 0.18);
    filter.frequency.exponentialRampToValueAtTime(500, t + 0.55);

    // Amplitude
    gain.gain.setValueAtTime(0, t);
    gain.gain.linearRampToValueAtTime(0.35, t + 0.08);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.55);

    osc.connect(filter);
    filter.connect(gain);
    gain.connect(this.ctx.destination);

    osc.start(t);
    osc.stop(t + 0.58);
  }

  // Purring (Belly Petting)
  startPurr() {
    this.init();
    if (this.purrOsc) return;

    const t = this.ctx.currentTime;
    this.purrOsc = this.ctx.createOscillator();
    const modOsc = this.ctx.createOscillator();
    const modGain = this.ctx.createGain();
    this.purrGain = this.ctx.createGain();

    this.purrOsc.type = 'sawtooth';
    this.purrOsc.frequency.value = 28; // Low motor rumble

    // Flutter modulation (24 Hz flutter)
    modOsc.type = 'sine';
    modOsc.frequency.value = 24;
    modGain.gain.value = 0.6;
    modOsc.connect(this.purrGain.gain);

    this.purrGain.gain.setValueAtTime(0.12, t);

    // Lowpass to make it warm
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.value = 160;

    this.purrOsc.connect(filter);
    filter.connect(this.purrGain);
    this.purrGain.connect(this.ctx.destination);

    this.purrOsc.start(t);
    modOsc.start(t);
  }

  stopPurr() {
    if (this.purrOsc) {
      try {
        this.purrOsc.stop();
        this.purrOsc.disconnect();
      } catch (e) {}
      this.purrOsc = null;
    }
  }

  // Bonk / Slap sound
  playBonk() {
    this.init();
    const t = this.ctx.currentTime;

    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(260, t);
    osc.frequency.exponentialRampToValueAtTime(45, t + 0.22);

    gain.gain.setValueAtTime(0.5, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.22);

    osc.connect(gain);
    gain.connect(this.ctx.destination);

    osc.start(t);
    osc.stop(t + 0.24);

    // Cartoon boing overtone
    setTimeout(() => {
      if (!this.ctx) return;
      const t2 = this.ctx.currentTime;
      const osc2 = this.ctx.createOscillator();
      const gain2 = this.ctx.createGain();
      osc2.type = 'triangle';
      osc2.frequency.setValueAtTime(180, t2);
      osc2.frequency.linearRampToValueAtTime(320, t2 + 0.25);
      gain2.gain.setValueAtTime(0.2, t2);
      gain2.gain.exponentialRampToValueAtTime(0.001, t2 + 0.25);
      osc2.connect(gain2);
      gain2.connect(this.ctx.destination);
      osc2.start(t2);
      osc2.stop(t2 + 0.26);
    }, 50);
  }

  // Giggle / Tickle
  playGiggle() {
    this.init();
    const count = 4;
    for (let i = 0; i < count; i++) {
      setTimeout(() => {
        if (!this.ctx) return;
        const t = this.ctx.currentTime;
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(600 + i * 40, t);
        osc.frequency.linearRampToValueAtTime(850 + i * 40, t + 0.08);
        gain.gain.setValueAtTime(0.2, t);
        gain.gain.exponentialRampToValueAtTime(0.001, t + 0.08);
        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(t);
        osc.stop(t + 0.09);
      }, i * 85);
    }
  }

  // Eating / Munch
  playMunch() {
    this.init();
    const bites = 3;
    for (let i = 0; i < bites; i++) {
      setTimeout(() => {
        if (!this.ctx) return;
        const t = this.ctx.currentTime;
        const bufferSize = this.ctx.sampleRate * 0.07;
        const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
        const data = buffer.getChannelData(0);
        for (let j = 0; j < bufferSize; j++) {
          data[j] = Math.random() * 2 - 1;
        }

        const noise = this.ctx.createBufferSource();
        noise.buffer = buffer;

        const filter = this.ctx.createBiquadFilter();
        filter.type = 'bandpass';
        filter.frequency.value = 1200 + Math.random() * 400;

        const gain = this.ctx.createGain();
        gain.gain.setValueAtTime(0.35, t);
        gain.gain.exponentialRampToValueAtTime(0.01, t + 0.06);

        noise.connect(filter);
        filter.connect(gain);
        gain.connect(this.ctx.destination);

        noise.start(t);
      }, i * 140);
    }
  }

  // Spicy Chili Fire
  playSpicyFire() {
    this.init();
    const t = this.ctx.currentTime;
    const bufferSize = this.ctx.sampleRate * 1.2;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * 0.8;
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(300, t);
    filter.frequency.linearRampToValueAtTime(2400, t + 0.4);
    filter.frequency.exponentialRampToValueAtTime(200, t + 1.2);

    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.4, t);
    gain.gain.exponentialRampToValueAtTime(0.01, t + 1.2);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.ctx.destination);
    noise.start(t);
  }

  // Fart Sound (Potty)
  playFart() {
    this.init();
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(110, t);
    osc.frequency.linearRampToValueAtTime(55, t + 0.35);

    gain.gain.setValueAtTime(0.4, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.4);

    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(t);
    osc.stop(t + 0.42);
  }

  // Water Flush / Shower
  playFlush() {
    this.init();
    const t = this.ctx.currentTime;
    const bufferSize = this.ctx.sampleRate * 1.5;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = Math.random() * 2 - 1;
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(800, t);
    filter.frequency.linearRampToValueAtTime(300, t + 1.4);

    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.35, t);
    gain.gain.exponentialRampToValueAtTime(0.01, t + 1.4);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.ctx.destination);
    noise.start(t);
  }

  // Bubble / Soap
  playBubble() {
    this.init();
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(500 + Math.random() * 400, t);
    osc.exponentialRampToValueAtTime(1200, t + 0.08);
    gain.gain.setValueAtTime(0.25, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.08);
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(t);
    osc.stop(t + 0.09);
  }

  // Coin Sound (Score / Reward)
  playCoin() {
    this.init();
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(987.77, t);
    osc.frequency.setValueAtTime(1318.51, t + 0.08);
    gain.gain.setValueAtTime(0.3, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.35);
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.start(t);
    osc.stop(t + 0.36);
  }

  // --- 2. Voice Recorder & High-Pitch Playback (Talking Tom Voice) ---

  async startListening(onStart, onStop, onPlaybackStart, onPlaybackEnd) {
    this.init();
    if (this.isRecording) return;

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.isRecording = true;
      this.audioChunks = [];
      this.mediaRecorder = new MediaRecorder(stream);

      if (onStart) onStart();

      this.mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) this.audioChunks.push(e.data);
      };

      this.mediaRecorder.onstop = async () => {
        this.isRecording = false;
        stream.getTracks().forEach(t => t.stop());
        if (onStop) onStop();

        const audioBlob = new Blob(this.audioChunks, { type: 'audio/ogg; codecs=opus' });
        const arrayBuffer = await audioBlob.arrayBuffer();

        try {
          const audioBuffer = await this.ctx.decodeAudioData(arrayBuffer);
          this.playSqueakyTomVoice(audioBuffer, onPlaybackStart, onPlaybackEnd);
        } catch (err) {
          this.speakText("म्याऊं! मुझे आपकी आवाज़ सुनाई दी!", onPlaybackStart, onPlaybackEnd);
        }
      };

      this.mediaRecorder.start();

      setTimeout(() => {
        if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
          this.mediaRecorder.stop();
        }
      }, 3500);

    } catch (err) {
      if (onStop) onStop();
      this.speakText("नमस्ते! अपना माइक ऑन करें या नीचे बॉक्स में लिखकर बोलें!", onPlaybackStart, onPlaybackEnd);
    }
  }

  stopListening() {
    if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
      this.mediaRecorder.stop();
    }
  }

  playSqueakyTomVoice(audioBuffer, onStart, onEnd) {
    this.init();
    const source = this.ctx.createBufferSource();
    source.buffer = audioBuffer;
    source.playbackRate.value = 1.45; // Squeaky high pitch!

    source.connect(this.analyser);
    this.analyser.connect(this.ctx.destination);

    if (onStart) onStart();

    let animId;
    const checkVolume = () => {
      if (!this.analyser) return;
      this.analyser.getByteFrequencyData(this.dataArray);
      let sum = 0;
      for (let i = 0; i < this.dataArray.length; i++) sum += this.dataArray[i];
      const avg = sum / this.dataArray.length;
      if (this.onAudioVolumeCallback) {
        this.onAudioVolumeCallback(avg / 128);
      }
      animId = requestAnimationFrame(checkVolume);
    };
    checkVolume();

    source.onended = () => {
      cancelAnimationFrame(animId);
      if (this.onAudioVolumeCallback) this.onAudioVolumeCallback(0);
      if (onEnd) onEnd();
    };

    source.start(0);
    this.currentPlayingSource = source;
  }

  // --- 3. Text-to-Speech (Hindi/English with high pitch) ---
  speakText(text, onStart, onEnd) {
    if (!('speechSynthesis' in window)) {
      this.playMeow();
      return;
    }

    window.speechSynthesis.cancel();
    const utter = new SpeechSynthesisUtterance(text);
    utter.pitch = 1.8;
    utter.rate = 1.15;

    const voices = window.speechSynthesis.getVoices();
    const hiVoice = voices.find(v => v.lang.includes('hi') || v.lang.includes('IN'));
    if (hiVoice) utter.voice = hiVoice;

    let lipInterval;
    utter.onstart = () => {
      if (onStart) onStart();
      lipInterval = setInterval(() => {
        if (this.onAudioVolumeCallback) {
          this.onAudioVolumeCallback(Math.random() * 0.8 + 0.2);
        }
      }, 90);
    };

    utter.onend = () => {
      clearInterval(lipInterval);
      if (this.onAudioVolumeCallback) this.onAudioVolumeCallback(0);
      if (onEnd) onEnd();
    };

    utter.onerror = () => {
      clearInterval(lipInterval);
      if (this.onAudioVolumeCallback) this.onAudioVolumeCallback(0);
      if (onEnd) onEnd();
    };

    window.speechSynthesis.speak(utter);
  }
}

window.tomAudio = new TomAudio();
