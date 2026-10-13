/* ====================================================
   MY TALKING TOM 2 - COMPLETE GAME ENGINE (game.js)
   Full Interactive Audio, Animations, AI Mimic & Rooms
   ==================================================== */

// --- GLOBAL GAME STATE ---
const state = {
  hunger: 70,     // 0 - 100
  hygiene: 80,    // 0 - 100
  energy: 85,     // 0 - 100
  fun: 80,        // 0 - 100
  coins: 150,
  currentRoom: 'living', // living, kitchen, bath, bedroom, game
  isSleeping: false,
  isRecording: false,
  isSpeaking: false,
  isChewing: false,
  activeAccessories: {
    hat: false,
    sunglasses: false,
    bowtie: false
  }
};

// --- AUDIO SYNTHESIZER (100% Procedural Web Audio API) ---
let audioCtx = null;

function initAudio() {
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
}

// Procedural Sound Effects
const SFX = {
  meow: function(pitch = 1.0) {
    initAudio();
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    const now = audioCtx.currentTime;

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(450 * pitch, now);
    osc.frequency.exponentialRampToValueAtTime(750 * pitch, now + 0.18);
    osc.frequency.exponentialRampToValueAtTime(320 * pitch, now + 0.5);

    gain.gain.setValueAtTime(0, now);
    gain.gain.linearRampToValueAtTime(0.28, now + 0.08);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.5);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.52);
  },

  purr: function() {
    initAudio();
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const lfo = audioCtx.createOscillator();
    const lfoGain = audioCtx.createGain();
    const gain = audioCtx.createGain();
    const now = audioCtx.currentTime;

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(70, now);

    lfo.type = 'sine';
    lfo.frequency.setValueAtTime(26, now); // 26Hz purr vibration
    lfoGain.gain.setValueAtTime(35, now);

    lfo.connect(lfoGain);
    lfoGain.connect(osc.frequency);

    gain.gain.setValueAtTime(0.18, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.6);

    osc.connect(gain);
    gain.connect(audioCtx.destination);

    lfo.start(now);
    osc.start(now);
    lfo.stop(now + 0.65);
    osc.stop(now + 0.65);
  },

  bonk: function() {
    initAudio();
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    const now = audioCtx.currentTime;

    osc.type = 'sine';
    osc.frequency.setValueAtTime(320, now);
    osc.frequency.exponentialRampToValueAtTime(70, now + 0.2);

    gain.gain.setValueAtTime(0.4, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.22);
  },

  giggle: function() {
    initAudio();
    if (!audioCtx) return;
    [0, 0.09, 0.18].forEach((offset, idx) => {
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      const now = audioCtx.currentTime + offset;
      osc.type = 'sine';
      osc.frequency.setValueAtTime(600 + idx * 80, now);
      osc.frequency.linearRampToValueAtTime(800 + idx * 80, now + 0.07);

      gain.gain.setValueAtTime(0.2, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.08);

      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start(now);
      osc.stop(now + 0.08);
    });
  },

  chew: function() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(220, now);
    osc.frequency.exponentialRampToValueAtTime(120, now + 0.1);

    gain.gain.setValueAtTime(0.3, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.1);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.1);
  },

  spicy: function() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(800, now);
    osc.frequency.exponentialRampToValueAtTime(1400, now + 0.4);
    gain.gain.setValueAtTime(0.35, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.45);
  },

  splash: function() {
    initAudio();
    if (!audioCtx) return;
    const bufferSize = audioCtx.sampleRate * 0.35;
    const buffer = audioCtx.createBuffer(1, bufferSize, audioCtx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.exp(-i / (bufferSize * 0.3));
    }
    const noise = audioCtx.createBufferSource();
    noise.buffer = buffer;

    const filter = audioCtx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.value = 850;

    const gain = audioCtx.createGain();
    gain.gain.value = 0.4;

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(audioCtx.destination);
    noise.start();
  },

  bubblePop: function() {
    initAudio();
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    const now = audioCtx.currentTime;
    osc.type = 'sine';
    osc.frequency.setValueAtTime(750, now);
    osc.frequency.exponentialRampToValueAtTime(1600, now + 0.08);

    gain.gain.setValueAtTime(0.2, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.09);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.09);
  },

  flush: function() {
    initAudio();
    if (!audioCtx) return;
    const bufferSize = audioCtx.sampleRate * 1.2;
    const buffer = audioCtx.createBuffer(1, bufferSize, audioCtx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * (1 - i / bufferSize);
    }
    const noise = audioCtx.createBufferSource();
    noise.buffer = buffer;
    const filter = audioCtx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(1200, audioCtx.currentTime);
    filter.frequency.linearRampToValueAtTime(300, audioCtx.currentTime + 1.2);

    noise.connect(filter);
    filter.connect(audioCtx.destination);
    noise.start();
  },

  coin: function() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(987.77, now);
    osc.frequency.setValueAtTime(1318.51, now + 0.08);

    gain.gain.setValueAtTime(0.25, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.36);
  }
};

// --- DOM ELEMENTS ---
const el = {
  app: document.getElementById('game-app'),
  scene: document.getElementById('scene-viewport'),
  barFun: document.getElementById('bar-fun'),
  barHunger: document.getElementById('bar-hunger'),
  barHygiene: document.getElementById('bar-hygiene'),
  barEnergy: document.getElementById('bar-energy'),
  coinCount: document.getElementById('coin-count'),
  speechBubble: document.getElementById('speech-bubble'),
  speechText: document.getElementById('speech-text'),
  nightOverlay: document.getElementById('night-overlay'),
  sleepZzz: document.getElementById('sleep-zzz'),
  dirtSpots: document.getElementById('dirt-spots'),
  soapLayer: document.getElementById('soap-layer'),
  tomSvg: document.getElementById('tom-svg'),
  fxLayer: document.getElementById('fx-layer'),

  // Drawers
  kitchenDrawer: document.getElementById('kitchen-drawer'),
  bathDrawer: document.getElementById('bath-drawer'),
  bedroomControls: document.getElementById('bedroom-controls'),
  wardrobeDrawer: document.getElementById('wardrobe-drawer'),
  talkInputDrawer: document.getElementById('talk-input-drawer'),
  micStatusBox: document.getElementById('mic-status-box'),
  micLabel: document.getElementById('mic-label'),

  // Lamp
  btnLamp: document.getElementById('btn-lamp'),
  lampLabel: document.getElementById('lamp-label'),

  // Facial elements
  pupilLeft: document.getElementById('pupil-left'),
  pupilRight: document.getElementById('pupil-right'),
  irisLeft: document.getElementById('iris-left'),
  irisRight: document.getElementById('iris-right'),
  eyelidLeft: document.getElementById('eyelid-left'),
  eyelidRight: document.getElementById('eyelid-right'),
  mouthLine: document.getElementById('mouth-line'),
  mouthOpen: document.getElementById('mouth-open'),
  mouthTongue: document.getElementById('mouth-tongue'),
  toothLeft: document.getElementById('tooth-left'),
  toothRight: document.getElementById('tooth-right'),
  dizzyStars: document.getElementById('dizzy-stars'),

  // Accessories
  accHat: document.getElementById('acc-hat'),
  accSunglasses: document.getElementById('acc-sunglasses'),
  accBowtie: document.getElementById('acc-bowtie'),
  accNightcap: document.getElementById('acc-nightcap'),

  // Navigation
  navTabs: document.querySelectorAll('.nav-tab'),
  btnMicToggle: document.getElementById('btn-mic-toggle'),
  btnWardrobeToggle: document.getElementById('btn-wardrobe-toggle'),

  // Mini-Game
  miniGameModal: document.getElementById('mini-game-modal'),
  btnCloseGame: document.getElementById('btn-close-game'),
  gameScoreDisplay: document.getElementById('game-score-display'),
  canvas: document.getElementById('mini-game-canvas'),
  btnMoveLeft: document.getElementById('btn-move-left'),
  btnMoveRight: document.getElementById('btn-move-right'),

  // Talk Input
  talkTextInput: document.getElementById('talk-text-input'),
  btnSendTalk: document.getElementById('btn-send-talk')
};

// --- UI UPDATERS ---
function updateStatusBars() {
  el.barFun.style.width = `${Math.max(0, Math.min(100, state.fun))}%`;
  el.barHunger.style.width = `${Math.max(0, Math.min(100, state.hunger))}%`;
  el.barHygiene.style.width = `${Math.max(0, Math.min(100, state.hygiene))}%`;
  el.barEnergy.style.width = `${Math.max(0, Math.min(100, state.energy))}%`;
  el.coinCount.textContent = state.coins;

  if (state.hygiene < 45) {
    el.dirtSpots.classList.remove('hidden');
  } else {
    el.dirtSpots.classList.add('hidden');
  }
}

function showSpeech(text, duration = 3000) {
  el.speechText.textContent = text;
  el.speechBubble.classList.remove('hidden');
  clearTimeout(el.speechBubble._timeout);
  el.speechBubble._timeout = setTimeout(() => {
    el.speechBubble.classList.add('hidden');
  }, duration);
}

function spawnFX(emoji, x, y) {
  const fx = document.createElement('div');
  fx.className = 'floating-fx';
  fx.textContent = emoji;
  fx.style.left = `${x}px`;
  fx.style.top = `${y}px`;
  el.fxLayer.appendChild(fx);
  setTimeout(() => fx.remove(), 1200);
}

// --- TOM EXPRESSIONS & ANIMATIONS ---
function setMouthOpen(open = true) {
  if (open) {
    el.mouthLine.classList.add('hidden');
    el.mouthOpen.classList.remove('hidden');
    el.mouthTongue.classList.remove('hidden');
    el.toothLeft.classList.remove('hidden');
    el.toothRight.classList.remove('hidden');
  } else {
    el.mouthLine.classList.remove('hidden');
    el.mouthOpen.classList.add('hidden');
    el.mouthTongue.classList.add('hidden');
    el.toothLeft.classList.add('hidden');
    el.toothRight.classList.add('hidden');
  }
}

function blinkEyes() {
  if (state.isSleeping) return;
  el.eyelidLeft.setAttribute('stroke-width', '40');
  el.eyelidRight.setAttribute('stroke-width', '40');
  setTimeout(() => {
    if (!state.isSleeping) {
      el.eyelidLeft.setAttribute('stroke-width', '0');
      el.eyelidRight.setAttribute('stroke-width', '0');
    }
  }, 180);
}

// Regular blinking
setInterval(() => {
  if (Math.random() > 0.3) {
    blinkEyes();
  }
}, 3500);

// Eye tracking pointer/finger
function setupEyeTracking() {
  const stage = document.getElementById('tom-stage');
  window.addEventListener('pointermove', (e) => {
    if (state.isSleeping) return;
    const rect = stage.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height * 0.35;

    const deltaX = (e.clientX - centerX) / (window.innerWidth / 2);
    const deltaY = (e.clientY - centerY) / (window.innerHeight / 2);

    const clampX = Math.max(-5, Math.min(5, deltaX * 5));
    const clampY = Math.max(-5, Math.min(5, deltaY * 5));

    el.pupilLeft.setAttribute('cx', 159 + clampX);
    el.pupilLeft.setAttribute('cy', 150 + clampY);
    el.pupilRight.setAttribute('cx', 241 + clampX);
    el.pupilRight.setAttribute('cy', 150 + clampY);
  });
}
setupEyeTracking();

// Reaction: Poked / Slapped
function triggerPokeReaction(e) {
  initAudio();
  if (state.isSleeping) {
    wakeUpTom();
    return;
  }
  SFX.bonk();
  SFX.meow(0.85);

  const rect = el.scene.getBoundingClientRect();
  const x = e ? e.clientX - rect.left : rect.width / 2;
  const y = e ? e.clientY - rect.top : rect.height / 2;
  spawnFX('💥', x, y);

  el.tomSvg.classList.add('anim-slap');
  el.dizzyStars.classList.remove('hidden');

  state.fun = Math.max(0, state.fun - 8);
  updateStatusBars();
  showSpeech("Arey! Aise mat maro yaar! 😵", 2000);

  setTimeout(() => {
    el.tomSvg.classList.remove('anim-slap');
    el.dizzyStars.classList.add('hidden');
  }, 1200);
}

// Reaction: Petting / Tickling Belly
let petCounter = 0;

function handlePetting(e) {
  initAudio();
  if (state.isSleeping) return;
  petCounter++;
  if (petCounter % 5 === 0) {
    SFX.purr();
    SFX.giggle();

    const rect = el.scene.getBoundingClientRect();
    const x = (e.clientX || (e.touches && e.touches[0].clientX)) - rect.left;
    const y = (e.clientY || (e.touches && e.touches[0].clientY)) - rect.top;
    const hearts = ['❤️', '💖', '✨', '🐾', '😻'];
    spawnFX(hearts[Math.floor(Math.random() * hearts.length)], x, y);

    setMouthOpen(true);
    el.tomSvg.classList.add('anim-purr');

    state.fun = Math.min(100, state.fun + 4);
    updateStatusBars();

    clearTimeout(handlePetting._timeout);
    handlePetting._timeout = setTimeout(() => {
      setMouthOpen(false);
      el.tomSvg.classList.remove('anim-purr');
    }, 400);

    if (petCounter % 25 === 0) {
      showSpeech("Haha! Gudgudi ho rahi hai! 😂❤️", 2500);
      state.coins += 2;
      SFX.coin();
      updateStatusBars();
    }
  }
}

// Setup Touch Reactions
function setupTouchReactions() {
  const belly = document.getElementById('tom-torso');
  const head = document.getElementById('tom-head-base');
  const feet = document.getElementById('tom-feet');

  belly.addEventListener('pointermove', handlePetting);
  belly.addEventListener('touchmove', handlePetting);
  head.addEventListener('click', (e) => triggerPokeReaction(e));

  feet.addEventListener('click', (e) => {
    initAudio();
    SFX.meow(1.3);
    const rect = el.scene.getBoundingClientRect();
    spawnFX('🐾', e.clientX - rect.left, e.clientY - rect.top);
    showSpeech("Mera paw mat chhoo! Hehe 🐾", 1800);
  });
}
setupTouchReactions();

// --- FEEDING TOM (KITCHEN) ---
function feedTom(foodType) {
  initAudio();
  if (state.isSleeping) {
    wakeUpTom();
    return;
  }
  if (state.isChewing) return;

  state.isChewing = true;
  setMouthOpen(true);
  el.tomSvg.classList.add('anim-chew');

  const foodData = {
    milk: { name: "Doodh", emoji: "🥛", hungerGain: 25, isSpicy: false, speech: "Mmm! Taza Doodh bohot mast tha! 🥛😋" },
    fish: { name: "Machhli", emoji: "🐟", hungerGain: 35, isSpicy: false, speech: "Waah! Lazeez machhli, maza aa gaya! 🐟✨" },
    cake: { name: "Cake", emoji: "🧁", hungerGain: 30, isSpicy: false, speech: "Mitha Cake! Mmm tasty! 🧁🎉" },
    chili: { name: "Mirchi", emoji: "🌶️", hungerGain: 10, isSpicy: true, speech: "BAAP RE! BOHOT TEEKHI MIRCHI HAI! 🔥🌶️" },
    watermelon: { name: "Tarbooz", emoji: "🍉", hungerGain: 20, isSpicy: false, speech: "Thanda meetha tarbooz! 🍉" }
  };

  const item = foodData[foodType];
  const rect = el.scene.getBoundingClientRect();
  spawnFX(item.emoji, rect.width / 2, rect.height * 0.4);

  let chews = 0;
  const chewInterval = setInterval(() => {
    chews++;
    SFX.chew();
    if (chews >= 4) {
      clearInterval(chewInterval);
      setMouthOpen(false);
      el.tomSvg.classList.remove('anim-chew');
      state.isChewing = false;

      if (item.isSpicy) {
        SFX.spicy();
        el.tomSvg.classList.add('anim-slap');
        spawnFX('🔥', rect.width / 2, rect.height * 0.35);
        spawnFX('🔥', rect.width / 2 - 30, rect.height * 0.35);
        spawnFX('🔥', rect.width / 2 + 30, rect.height * 0.35);
        showSpeech(item.speech, 3500);
        setTimeout(() => el.tomSvg.classList.remove('anim-slap'), 1200);
      } else {
        SFX.purr();
        state.hunger = Math.min(100, state.hunger + item.hungerGain);
        state.fun = Math.min(100, state.fun + 10);
        state.coins += 5;
        SFX.coin();
        updateStatusBars();
        showSpeech(item.speech, 2500);
      }
    }
  }, 240);
}

document.querySelectorAll('.food-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const food = btn.getAttribute('data-food');
    feedTom(food);
  });
});

// --- BATHROOM SYSTEM (NAHANA) ---
let soapBubblesCount = 0;

function applySoap() {
  initAudio();
  SFX.bubblePop();
  soapBubblesCount++;

  const bubble = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  const cx = 130 + Math.random() * 140;
  const cy = 200 + Math.random() * 220;
  const r = 8 + Math.random() * 14;

  bubble.setAttribute('cx', cx);
  bubble.setAttribute('cy', cy);
  bubble.setAttribute('r', r);
  bubble.setAttribute('fill', 'rgba(255, 255, 255, 0.75)');
  bubble.setAttribute('stroke', '#81d4fa');
  bubble.setAttribute('stroke-width', '2');
  bubble.setAttribute('class', 'soap-bubble-item');

  el.soapLayer.appendChild(bubble);

  const rect = el.scene.getBoundingClientRect();
  spawnFX('🫧', rect.width / 2 + (Math.random() * 80 - 40), rect.height * 0.5);

  if (soapBubblesCount >= 6) {
    showSpeech("Sabun lag gaya! Ab Shower se dho do! 🚿", 2500);
  }
}

function showerTom() {
  initAudio();
  SFX.splash();
  const rect = el.scene.getBoundingClientRect();

  const bubbles = el.soapLayer.querySelectorAll('.soap-bubble-item');
  bubbles.forEach((b, i) => {
    setTimeout(() => {
      SFX.bubblePop();
      b.remove();
    }, i * 60);
  });
  soapBubblesCount = 0;

  for (let i = 0; i < 5; i++) {
    setTimeout(() => {
      spawnFX('💦', rect.width / 2 + (Math.random() * 90 - 45), rect.height * 0.35 + i * 15);
      spawnFX('✨', rect.width / 2 + (Math.random() * 90 - 45), rect.height * 0.45);
    }, i * 120);
  }

  state.hygiene = 100;
  state.fun = Math.min(100, state.fun + 15);
  state.coins += 10;
  SFX.coin();
  updateStatusBars();
  showSpeech("Aah! Ekdum chaka chak saaf ho gaya! ✨🛁", 3000);
}

function toiletTom() {
  initAudio();
  SFX.flush();
  showSpeech("Shh... Tom toilet use kar raha hai! 🚽", 2500);
  setTimeout(() => {
    state.hygiene = Math.min(100, state.hygiene + 20);
    state.fun = Math.min(100, state.fun + 10);
    updateStatusBars();
    showSpeech("Flushed! Pet halka ho gaya! 💨😂", 2500);
  }, 1200);
}

document.getElementById('btn-soap').addEventListener('click', applySoap);
document.getElementById('btn-shower').addEventListener('click', showerTom);
document.getElementById('btn-potty').addEventListener('click', toiletTom);

// --- BEDROOM SYSTEM (SLEEPING & LIGHTS) ---
function toggleSleep() {
  initAudio();
  state.isSleeping = !state.isSleeping;

  if (state.isSleeping) {
    el.nightOverlay.classList.remove('hidden');
    el.sleepZzz.classList.remove('hidden');
    el.accNightcap.classList.remove('hidden');
    el.eyelidLeft.setAttribute('stroke-width', '40');
    el.eyelidRight.setAttribute('stroke-width', '40');
    el.lampLabel.textContent = "Batti Jalayein 💡";
    showSpeech("Shh... Tom so raha hai... Zzz 😴", 2500);

    window.sleepInterval = setInterval(() => {
      if (state.isSleeping) {
        state.energy = Math.min(100, state.energy + 8);
        updateStatusBars();
        if (state.energy >= 100) {
          showSpeech("Tom ki neend poori ho gayi! Jagao ise! ☀️", 3000);
        }
      }
    }, 1500);
  } else {
    wakeUpTom();
  }
}

function wakeUpTom() {
  if (!state.isSleeping) return;
  state.isSleeping = false;
  clearInterval(window.sleepInterval);
  el.nightOverlay.classList.add('hidden');
  el.sleepZzz.classList.add('hidden');
  el.accNightcap.classList.add('hidden');
  el.eyelidLeft.setAttribute('stroke-width', '0');
  el.eyelidRight.setAttribute('stroke-width', '0');
  el.lampLabel.textContent = "Batti Band Karo 💡";
  SFX.meow(1.1);
  showSpeech("Good Morning! Main jag gaya! 😸☀️", 2500);
}

el.btnLamp.addEventListener('click', toggleSleep);

// --- WARDROBE SYSTEM ---
function toggleAccessory(accName) {
  initAudio();
  state.activeAccessories[accName] = !state.activeAccessories[accName];
  const isNowActive = state.activeAccessories[accName];

  if (accName === 'hat') {
    el.accHat.classList.toggle('hidden', !isNowActive);
  } else if (accName === 'sunglasses') {
    el.accSunglasses.classList.toggle('hidden', !isNowActive);
  } else if (accName === 'bowtie') {
    el.accBowtie.classList.toggle('hidden', !isNowActive);
  }

  SFX.coin();
  showSpeech(isNowActive ? "Dekho mera naya style! 😎✨" : "Style reset!", 1800);
}

document.querySelectorAll('.wardrobe-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const acc = btn.getAttribute('data-acc');
    toggleAccessory(acc);
  });
});

// --- ROOM NAVIGATION ---
function switchRoom(roomName) {
  initAudio();
  state.currentRoom = roomName;

  el.navTabs.forEach(tab => {
    tab.classList.toggle('active', tab.getAttribute('data-room') === roomName);
  });

  el.kitchenDrawer.classList.add('hidden');
  el.bathDrawer.classList.add('hidden');
  el.bedroomControls.classList.add('hidden');
  el.wardrobeDrawer.classList.add('hidden');
  el.talkInputDrawer.classList.add('hidden');

  el.scene.className = `room-${roomName}`;

  if (roomName === 'living') {
    showSpeech("Mere pyare ghar mein swagat hai! 🛋️❤️", 2000);
  } else if (roomName === 'kitchen') {
    el.kitchenDrawer.classList.remove('hidden');
    showSpeech("Bhookh lagi hai! Kuch khilao na! 🍽️😋", 2500);
  } else if (roomName === 'bath') {
    el.bathDrawer.classList.remove('hidden');
    showSpeech("Chalo nahane chalein! 🛁🧼", 2000);
  } else if (roomName === 'bedroom') {
    el.bedroomControls.classList.remove('hidden');
    showSpeech("Thak gaya hoon! Sone ka waqt? 🛏️😴", 2000);
  } else if (roomName === 'game') {
    openMiniGame();
  }
}

el.navTabs.forEach(tab => {
  tab.addEventListener('click', () => {
    const room = tab.getAttribute('data-room');
    if (room) switchRoom(room);
  });
});

el.btnWardrobeToggle.addEventListener('click', () => {
  initAudio();
  el.wardrobeDrawer.classList.toggle('hidden');
});

// --- CORE TALKING TOM FEATURE: MIC RECORDING & HELIUM PITCH SHIFT ---
let mediaRecorder = null;
let audioChunks = [];
let micStream = null;

async function startMicRecording() {
  initAudio();
  try {
    micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    mediaRecorder = new MediaRecorder(micStream);
    audioChunks = [];

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) audioChunks.push(e.data);
    };

    mediaRecorder.onstop = async () => {
      const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
      playBackWithPitchShift(audioBlob);
    };

    mediaRecorder.start();
    state.isRecording = true;
    el.btnMicToggle.classList.add('recording');
    el.micStatusBox.classList.remove('hidden');
    el.micLabel.textContent = "Tom Sun Raha Hai... Bolo! 🎤";
    showSpeech("Main dhyan se sun raha hoon... 👂", 2000);

    setTimeout(() => {
      if (state.isRecording) {
        stopMicRecording();
      }
    }, 4500);

  } catch (err) {
    console.warn("Microphone access unavailable or denied:", err);
    el.talkInputDrawer.classList.toggle('hidden');
    showSpeech("Mic permission nahi mili! Yahan likh kar bolo 💬", 3000);
  }
}

function stopMicRecording() {
  if (mediaRecorder && state.isRecording) {
    mediaRecorder.stop();
    if (micStream) {
      micStream.getTracks().forEach(track => track.stop());
    }
  }
  state.isRecording = false;
  el.btnMicToggle.classList.remove('recording');
  el.micStatusBox.classList.add('hidden');
}

async function playBackWithPitchShift(audioBlob) {
  initAudio();
  if (!audioCtx) return;

  const arrayBuffer = await audioBlob.arrayBuffer();
  try {
    const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer);
    const source = audioCtx.createBufferSource();
    source.buffer = audioBuffer;

    // Helium Cat Voice: 1.45x playback rate
    source.playbackRate.value = 1.45;

    source.connect(audioCtx.destination);
    source.start();

    state.isSpeaking = true;
    const mouthInterval = setInterval(() => {
      setMouthOpen(Math.random() > 0.4);
    }, 120);

    const playDuration = (audioBuffer.duration / 1.45) * 1000;
    showSpeech("Hehe! Maine sun liya! 🗣️😸", playDuration);

    setTimeout(() => {
      clearInterval(mouthInterval);
      setMouthOpen(false);
      state.isSpeaking = false;
    }, playDuration + 100);

  } catch (err) {
    console.error("Audio decode error:", err);
    tomSpeakFallback("Meow! Maine aapki awaz suni!");
  }
}

// Fallback Speech Synthesis
function tomSpeakFallback(text) {
  initAudio();
  showSpeech(text, 3500);

  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.pitch = 1.85; // High pitch helium voice
    utterance.rate = 1.25;  // Fast cute cat rate
    utterance.lang = 'hi-IN';

    state.isSpeaking = true;
    const mouthInterval = setInterval(() => {
      setMouthOpen(Math.random() > 0.35);
    }, 130);

    utterance.onend = () => {
      clearInterval(mouthInterval);
      setMouthOpen(false);
      state.isSpeaking = false;
    };
    utterance.onerror = () => {
      clearInterval(mouthInterval);
      setMouthOpen(false);
      state.isSpeaking = false;
    };

    window.speechSynthesis.speak(utterance);
  } else {
    SFX.meow(1.4);
    setMouthOpen(true);
    setTimeout(() => setMouthOpen(false), 800);
  }
}

el.btnMicToggle.addEventListener('click', () => {
  if (state.isRecording) {
    stopMicRecording();
  } else {
    startMicRecording();
  }
});

// Quick talk input events
el.btnSendTalk.addEventListener('click', () => {
  const text = el.talkTextInput.value.trim();
  if (text) {
    tomSpeakFallback(text);
    el.talkTextInput.value = '';
  }
});

document.querySelectorAll('.phrase-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    const phrase = chip.getAttribute('data-phrase');
    tomSpeakFallback(phrase);
  });
});

// --- MINI-GAME: MACHHLI PAKDO ---
let gameLoopId = null;
let gameScore = 0;
let gameCoinsEarned = 0;
let playerX = 140;
let items = [];
let gameRunning = false;

function openMiniGame() {
  el.miniGameModal.classList.remove('hidden');
  startMiniGame();
}

function closeMiniGame() {
  gameRunning = false;
  if (gameLoopId) cancelAnimationFrame(gameLoopId);
  el.miniGameModal.classList.add('hidden');
  state.coins += gameCoinsEarned;
  state.fun = Math.min(100, state.fun + 25);
  updateStatusBars();
  switchRoom('living');
}

el.btnCloseGame.addEventListener('click', closeMiniGame);

function startMiniGame() {
  gameScore = 0;
  gameCoinsEarned = 0;
  playerX = 140;
  items = [];
  gameRunning = true;
  el.gameScoreDisplay.textContent = `Score: 0 | 🪙 0`;

  const canvas = el.canvas;
  const ctx = canvas.getContext('2d');

  let lastSpawn = Date.now();

  function spawnGameItem() {
    const types = [
      { type: 'fish', emoji: '🐟', score: 10, speed: 2.8, isBomb: false },
      { type: 'coin', emoji: '🪙', score: 5, speed: 3.2, isBomb: false, coin: 2 },
      { type: 'milk', emoji: '🥛', score: 15, speed: 2.5, isBomb: false },
      { type: 'bomb', emoji: '💣', score: -20, speed: 3.5, isBomb: true }
    ];
    const rand = Math.random();
    let selected = types[0];
    if (rand < 0.35) selected = types[0];
    else if (rand < 0.65) selected = types[1];
    else if (rand < 0.85) selected = types[2];
    else selected = types[3];

    items.push({
      x: 25 + Math.random() * (canvas.width - 50),
      y: -25,
      ...selected
    });
  }

  function loop() {
    if (!gameRunning) return;

    ctx.fillStyle = '#0288d1';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
    ctx.lineWidth = 1;
    for (let y = 0; y < canvas.height; y += 40) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(canvas.width, y);
      ctx.stroke();
    }

    if (Date.now() - lastSpawn > 800) {
      spawnGameItem();
      lastSpawn = Date.now();
    }

    for (let i = items.length - 1; i >= 0; i--) {
      const it = items[i];
      it.y += it.speed;

      ctx.font = '26px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(it.emoji, it.x, it.y);

      if (it.y >= canvas.height - 45 && it.y <= canvas.height - 15) {
        if (Math.abs(it.x - playerX) < 40) {
          if (it.isBomb) {
            SFX.bonk();
            gameScore = Math.max(0, gameScore - 20);
          } else {
            SFX.bubblePop();
            gameScore += it.score;
            if (it.coin) {
              SFX.coin();
              gameCoinsEarned += it.coin;
            }
          }
          el.gameScoreDisplay.textContent = `Score: ${gameScore} | 🪙 ${gameCoinsEarned}`;
          items.splice(i, 1);
          continue;
        }
      }

      if (it.y > canvas.height + 30) {
        items.splice(i, 1);
      }
    }

    const basketY = canvas.height - 35;
    ctx.fillStyle = '#ff9800';
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(playerX - 35, basketY, 70, 24, [0, 0, 12, 12]);
    } else {
      ctx.rect(playerX - 35, basketY, 70, 24);
    }
    ctx.fill();
    ctx.fillStyle = '#e65100';
    ctx.fillRect(playerX - 35, basketY, 70, 5);

    ctx.font = '24px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('🐱', playerX, basketY + 18);

    gameLoopId = requestAnimationFrame(loop);
  }

  function setPlayerPos(clientX) {
    const rect = canvas.getBoundingClientRect();
    const x = ((clientX - rect.left) / rect.width) * canvas.width;
    playerX = Math.max(40, Math.min(canvas.width - 40, x));
  }

  canvas.onpointermove = (e) => setPlayerPos(e.clientX);
  canvas.ontouchmove = (e) => {
    if (e.touches[0]) setPlayerPos(e.touches[0].clientX);
  };

  el.btnMoveLeft.onclick = () => { playerX = Math.max(40, playerX - 35); };
  el.btnMoveRight.onclick = () => { playerX = Math.min(canvas.width - 40, playerX + 35); };

  loop();
}

// --- STAT DECAY TIMER ---
setInterval(() => {
  if (!state.isSleeping) {
    state.hunger = Math.max(0, state.hunger - 1);
    state.fun = Math.max(0, state.fun - 1);
    state.energy = Math.max(0, state.energy - 1);
    state.hygiene = Math.max(0, state.hygiene - 1);
    updateStatusBars();

    if (state.hunger <= 20 && Math.random() < 0.25) {
      showSpeech("Mujhe bhookh lagi hai! Rasoi mein le chalo 🍽️🥺", 3000);
      SFX.meow(0.9);
    }
  }
}, 7000);

// --- INITIALIZE GAME ---
window.addEventListener('DOMContentLoaded', () => {
  updateStatusBars();
  showSpeech("Namaste! Main hoon Tom! Mujhse khelo! 😸🎉", 3500);

  window.addEventListener('click', () => initAudio(), { once: true });
  window.addEventListener('touchstart', () => initAudio(), { once: true });
});
