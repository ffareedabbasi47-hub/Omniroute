/* ========================================================
   Talking Tom Cat 2 - Ultimate Web Game
   JavaScript Engine: Audio, Animations, Physics & Gameplay
   ======================================================== */

// --- Global State ---
const State = {
  hunger: 80,
  hygiene: 75,
  energy: 90,
  happy: 85,
  coins: 150,
  level: 1,
  xp: 40,
  activeRoom: 'living',
  activeTool: null,
  isSleeping: false,
  isSpeaking: false,
  isDizzy: false,
  isEating: false,
  hat: 'none',
  glasses: 'none',
  fur: 'grey',
  accessory: 'none'
};

// --- Web Audio API Synthesizer (Zero External Dependencies) ---
let audioCtx = null;

function initAudio() {
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  }
  if (audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
}

const SoundFX = {
  meow(pitch = 520) {
    initAudio();
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    const now = audioCtx.currentTime;

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(pitch, now);
    osc.frequency.exponentialRampToValueAtTime(pitch * 1.3, now + 0.15);
    osc.frequency.exponentialRampToValueAtTime(pitch * 0.75, now + 0.45);

    gain.gain.setValueAtTime(0.01, now);
    gain.gain.linearRampToValueAtTime(0.28, now + 0.08);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.45);
  },

  purr() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    const lfo = audioCtx.createOscillator();
    const lfoGain = audioCtx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(45, now);

    lfo.type = 'sine';
    lfo.frequency.setValueAtTime(22, now); // 22Hz purr rumble
    lfoGain.gain.setValueAtTime(0.08, now);

    lfo.connect(gain.gain);
    gain.gain.setValueAtTime(0.12, now);
    gain.gain.linearRampToValueAtTime(0, now + 0.5);

    osc.connect(gain);
    gain.connect(audioCtx.destination);

    osc.start(now);
    lfo.start(now);
    osc.stop(now + 0.5);
    lfo.stop(now + 0.5);
  },

  tickle() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    for (let i = 0; i < 3; i++) {
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      const t = now + i * 0.08;

      osc.type = 'sine';
      osc.frequency.setValueAtTime(1100 + i * 150, t);
      osc.frequency.exponentialRampToValueAtTime(1400, t + 0.06);

      gain.gain.setValueAtTime(0.15, t);
      gain.gain.exponentialRampToValueAtTime(0.01, t + 0.06);

      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start(t);
      osc.stop(t + 0.06);
    }
  },

  slap() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(280, now);
    osc.frequency.exponentialRampToValueAtTime(80, now + 0.2);

    gain.gain.setValueAtTime(0.35, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.2);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.2);
  },

  boing() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(260, now);
    osc.frequency.exponentialRampToValueAtTime(680, now + 0.25);

    gain.gain.setValueAtTime(0.25, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.25);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.25);
  },

  crunch() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const bufferSize = audioCtx.sampleRate * 0.15;
    const buffer = audioCtx.createBuffer(1, bufferSize, audioCtx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.exp(-i / (bufferSize * 0.3));
    }
    const noise = audioCtx.createBufferSource();
    noise.buffer = buffer;

    const filter = audioCtx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(1400, now);

    const gain = audioCtx.createGain();
    gain.gain.setValueAtTime(0.3, now);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(audioCtx.destination);
    noise.start(now);
  },

  gulp() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(600, now);
    osc.frequency.exponentialRampToValueAtTime(220, now + 0.18);

    gain.gain.setValueAtTime(0.25, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.18);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.18);
  },

  burp() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(95, now);
    osc.frequency.linearRampToValueAtTime(120, now + 0.1);
    osc.frequency.linearRampToValueAtTime(80, now + 0.35);

    gain.gain.setValueAtTime(0.28, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.35);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.35);
  },

  splash() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(700, now);
    osc.frequency.exponentialRampToValueAtTime(1300, now + 0.08);

    gain.gain.setValueAtTime(0.2, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.12);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.12);
  },

  snore() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(140, now);
    osc.frequency.linearRampToValueAtTime(110, now + 0.8);

    gain.gain.setValueAtTime(0.02, now);
    gain.gain.linearRampToValueAtTime(0.12, now + 0.4);
    gain.gain.linearRampToValueAtTime(0.01, now + 0.8);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(now);
    osc.stop(now + 0.8);
  },

  coin() {
    initAudio();
    if (!audioCtx) return;
    const now = audioCtx.currentTime;
    const osc1 = audioCtx.createOscillator();
    const osc2 = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc1.type = 'sine';
    osc2.type = 'sine';
    osc1.frequency.setValueAtTime(987.77, now); // B5
    osc2.frequency.setValueAtTime(1318.51, now + 0.08); // E6

    gain.gain.setValueAtTime(0.2, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.35);

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(audioCtx.destination);

    osc1.start(now);
    osc1.stop(now + 0.08);
    osc2.start(now + 0.08);
    osc2.stop(now + 0.35);
  },

  levelUp() {
    initAudio();
    if (!audioCtx) return;
    const notes = [523.25, 659.25, 783.99, 1046.50]; // C5, E5, G5, C6
    const now = audioCtx.currentTime;
    notes.forEach((freq, idx) => {
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      const t = now + idx * 0.1;

      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, t);

      gain.gain.setValueAtTime(0.25, t);
      gain.gain.exponentialRampToValueAtTime(0.01, t + 0.25);

      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start(t);
      osc.stop(t + 0.25);
    });
  }
};

// --- DOM References ---
const characterStage = document.getElementById('character-stage');
const tomSvg = document.getElementById('tom-svg');
const speechBubble = document.getElementById('speech-bubble');
const eyeLeftPupil = document.getElementById('eye-left-pupil');
const eyeRightPupil = document.getElementById('eye-right-pupil');
const eyeLeftIris = document.getElementById('eye-left-iris');
const eyeRightIris = document.getElementById('eye-right-iris');
const mouthClosed = document.getElementById('mouth-closed');
const mouthOpen = document.getElementById('mouth-open');
const eyesEyelids = document.querySelectorAll('.cat-eyelid');
const fxContainer = document.getElementById('fx-container');
const micBtn = document.getElementById('mic-btn');

// --- Haptic Feedback Helper ---
function vibrate(ms = 25) {
  if (navigator.vibrate) {
    try { navigator.vibrate(ms); } catch (e) {}
  }
}

// --- Status & Progression System ---
function updateHUD() {
  document.getElementById('val-hunger').style.width = `${State.hunger}%`;
  document.getElementById('val-hygiene').style.width = `${State.hygiene}%`;
  document.getElementById('val-energy').style.width = `${State.energy}%`;
  document.getElementById('val-happy').style.width = `${State.happy}%`;
  document.getElementById('user-coins').innerText = State.coins;
  document.getElementById('user-level').innerText = `Lv. ${State.level}`;
  saveProgress();
}

function addXP(amount) {
  State.xp += amount;
  if (State.xp >= 100) {
    State.xp -= 100;
    State.level += 1;
    State.coins += 50;
    SoundFX.levelUp();
    showSpeechBubble(`🎉 Level Up! Level ${State.level} ban gaye! (+50 🪙)`);
    spawnFloatingText(characterStage.offsetLeft + 160, characterStage.offsetTop + 40, 'LEVEL UP! 🌟');
  }
  updateHUD();
}

function addCoins(amount) {
  State.coins += amount;
  SoundFX.coin();
  updateHUD();
}

// --- Floating FX Generator ---
function spawnFloatingText(x, y, text, color = '#f59e0b') {
  const el = document.createElement('div');
  el.className = 'floating-heart';
  el.innerText = text;
  el.style.left = `${x}px`;
  el.style.top = `${y}px`;
  el.style.color = color;
  el.style.fontWeight = 'bold';
  fxContainer.appendChild(el);
  setTimeout(() => el.remove(), 1200);
}

function spawnHeart(x, y) {
  const emojis = ['💖', '😻', '✨', '🐾', '💕'];
  const emoji = emojis[Math.floor(Math.random() * emojis.length)];
  const el = document.createElement('div');
  el.className = 'floating-heart';
  el.innerText = emoji;
  el.style.left = `${x + (Math.random() * 40 - 20)}px`;
  el.style.top = `${y}px`;
  fxContainer.appendChild(el);
  setTimeout(() => el.remove(), 1200);
}

function showSpeechBubble(text, duration = 3000) {
  speechBubble.innerText = text;
  speechBubble.classList.add('active');
  clearTimeout(speechBubble._timer);
  speechBubble._timer = setTimeout(() => {
    speechBubble.classList.remove('active');
  }, duration);
}

// --- Cat Face & Expression Controllers ---
function setCatMouth(isOpen) {
  if (isOpen) {
    mouthClosed.style.display = 'none';
    mouthOpen.style.display = 'block';
  } else {
    mouthClosed.style.display = 'block';
    mouthOpen.style.display = 'none';
  }
}

function setEyesClosed(closed) {
  eyesEyelids.forEach(el => {
    el.style.display = closed ? 'block' : 'none';
  });
  if (closed) {
    eyeLeftPupil.style.opacity = '0';
    eyeRightPupil.style.opacity = '0';
  } else {
    eyeLeftPupil.style.opacity = '1';
    eyeRightPupil.style.opacity = '1';
  }
}

// Natural Blinking Loop
setInterval(() => {
  if (State.isSleeping || State.isDizzy) return;
  setEyesClosed(true);
  setTimeout(() => {
    if (!State.isSleeping && !State.isDizzy) setEyesClosed(false);
  }, 160);
}, 3800);

// --- Eye Tracking (Pupils follow pointer/touch) ---
function trackEyes(clientX, clientY) {
  if (State.isSleeping || State.isDizzy) return;
  const rect = tomSvg.getBoundingClientRect();
  const catCenterX = rect.left + rect.width / 2;
  const catCenterY = rect.top + rect.height * 0.35;

  const dx = clientX - catCenterX;
  const dy = clientY - catCenterY;
  const angle = Math.atan2(dy, dx);
  const distance = Math.min(Math.hypot(dx, dy) / 40, 4.5);

  const pupilX = Math.cos(angle) * distance;
  const pupilY = Math.sin(angle) * distance;

  eyeLeftPupil.setAttribute('transform', `translate(${pupilX}, ${pupilY})`);
  eyeRightPupil.setAttribute('transform', `translate(${pupilX}, ${pupilY})`);
  eyeLeftIris.setAttribute('transform', `translate(${pupilX * 0.6}, ${pupilY * 0.6})`);
  eyeRightIris.setAttribute('transform', `translate(${pupilX * 0.6}, ${pupilY * 0.6})`);
}

window.addEventListener('pointermove', (e) => {
  trackEyes(e.clientX, e.clientY);
});

// --- Cat Interactive Touch & Petting Behaviors ---
let petCount = 0;
let lastPetTime = 0;

function handlePet(e) {
  vibrate(20);
  const now = Date.now();
  if (now - lastPetTime > 250) {
    SoundFX.purr();
    lastPetTime = now;
  }
  
  State.happy = Math.min(100, State.happy + 1.5);
  addXP(1);
  updateHUD();

  tomSvg.classList.add('petting-happy');
  setEyesClosed(true);
  const rect = characterStage.getBoundingClientRect();
  const x = (e.clientX || (e.touches && e.touches[0].clientX) || (rect.left + rect.width / 2)) - rect.left;
  const y = (e.clientY || (e.touches && e.touches[0].clientY) || (rect.top + 80)) - rect.top;
  spawnHeart(x + rect.left, y + rect.top);

  clearTimeout(tomSvg._petTimeout);
  tomSvg._petTimeout = setTimeout(() => {
    tomSvg.classList.remove('petting-happy');
    if (!State.isSleeping) setEyesClosed(false);
  }, 450);
}

function handleHeadTap() {
  petCount++;
  if (petCount >= 4) {
    triggerDizzy();
    petCount = 0;
    return;
  }
  vibrate(35);
  SoundFX.meow(600);
  showSpeechBubble('Meow! ❤️');
  State.happy = Math.min(100, State.happy + 2);
  updateHUD();
}

function triggerDizzy() {
  State.isDizzy = true;
  vibrate(60);
  SoundFX.slap();
  SoundFX.boing();
  tomSvg.classList.add('dizzy');
  showSpeechBubble('Arey chakkar aa gaya! 😵💫');
  setEyesClosed(true);

  setTimeout(() => {
    tomSvg.classList.remove('dizzy');
    State.isDizzy = false;
    setEyesClosed(false);
  }, 900);
}

function handleBellyTickle() {
  vibrate(30);
  SoundFX.tickle();
  tomSvg.classList.add('tickled');
  setCatMouth(true);
  showSpeechBubble('Hahaha! Gudgudi ho rahi hai! 😂');
  State.happy = Math.min(100, State.happy + 3);
  addXP(2);
  updateHUD();

  clearTimeout(tomSvg._tickleTimeout);
  tomSvg._tickleTimeout = setTimeout(() => {
    tomSvg.classList.remove('tickled');
    setCatMouth(false);
  }, 600);
}

function handleFootTap() {
  vibrate(40);
  SoundFX.meow(420);
  showSpeechBubble('Meoww! Mere pair pe mat maaro! 🐾');
}

function handleTailTap() {
  vibrate(45);
  SoundFX.slap();
  showSpeechBubble('Meri poonch mat kheecho! 😾');
  const tail = document.getElementById('cat-tail');
  if (tail) {
    tail.style.transform = 'rotate(-25deg)';
    setTimeout(() => { tail.style.transform = ''; }, 300);
  }
}

// Bind Touch Hotspots
document.querySelector('.zone-head').addEventListener('pointerdown', (e) => {
  handleHeadTap();
  handlePet(e);
});
document.querySelector('.zone-head').addEventListener('pointermove', (e) => {
  if (e.buttons > 0) handlePet(e);
});
document.querySelector('.zone-belly').addEventListener('pointerdown', handleBellyTickle);
document.querySelector('.zone-belly').addEventListener('pointermove', (e) => {
  if (e.buttons > 0) handleBellyTickle();
});
document.querySelector('.zone-left-foot').addEventListener('pointerdown', handleFootTap);
document.querySelector('.zone-right-foot').addEventListener('pointerdown', handleFootTap);
document.querySelector('.zone-tail').addEventListener('pointerdown', handleTailTap);

// --- Room Switching System ---
function switchRoom(roomId) {
  if (State.isSleeping && roomId !== 'bedroom') {
    showSpeechBubble('Tom so raha hai! Pehle light jalao! 😴');
    return;
  }

  State.activeRoom = roomId;
  document.querySelectorAll('.room-bg').forEach(el => el.classList.remove('active'));
  document.getElementById(`bg-${roomId}`).classList.add('active');

  document.querySelectorAll('.action-tray').forEach(el => el.style.display = 'none');
  const roomTray = document.getElementById(`tray-${roomId}`);
  if (roomTray) roomTray.style.display = 'flex';

  document.querySelectorAll('.nav-tab').forEach(el => {
    el.classList.toggle('active', el.dataset.room === roomId);
  });

  SoundFX.boing();
}

document.querySelectorAll('.nav-tab').forEach(tab => {
  tab.addEventListener('click', () => {
    switchRoom(tab.dataset.room);
  });
});

// --- Kitchen: Food Feeding System ---
const Foods = {
  milk: { name: 'Doodh', hunger: 15, xp: 5, emoji: '🥛', effect: 'normal' },
  fish: { name: 'Machhli', hunger: 25, xp: 8, emoji: '🐟', effect: 'crunch' },
  burger: { name: 'Burger', hunger: 35, xp: 12, emoji: '🍔', effect: 'big' },
  chili: { name: 'Mirchi', hunger: 10, xp: 15, emoji: '🌶️', effect: 'spicy' },
  icecream: { name: 'Ice Cream', hunger: 20, xp: 10, emoji: '🍦', effect: 'freeze' }
};

function feedTom(foodKey) {
  if (State.isEating) return;
  const food = Foods[foodKey];
  if (!food) return;

  State.isEating = true;
  vibrate(30);

  let chews = 0;
  setCatMouth(true);
  const chewInterval = setInterval(() => {
    chews++;
    SoundFX.crunch();
    setCatMouth(chews % 2 === 1);
    if (chews >= 5) {
      clearInterval(chewInterval);
      setCatMouth(false);
      SoundFX.gulp();

      if (food.effect === 'spicy') {
        SoundFX.slap();
        tomSvg.style.filter = 'drop-shadow(0 0 20px #ef4444) hue-rotate(-50deg)';
        showSpeechBubble('AAG LAG GAYI! BOHOT TIKHA HAI! 🔥🌶️');
        spawnFloatingText(characterStage.offsetLeft + 160, characterStage.offsetTop + 100, '🔥 AAAAAGH! 🔥', '#ef4444');
        setTimeout(() => {
          tomSvg.style.filter = '';
          SoundFX.burp();
          State.isEating = false;
        }, 1800);
      } else if (food.effect === 'freeze') {
        tomSvg.style.filter = 'hue-rotate(160deg) drop-shadow(0 0 15px #67e8f9)';
        showSpeechBubble('Brrr! Brain Freeze ho gaya! 🥶🍦');
        setTimeout(() => {
          tomSvg.style.filter = '';
          State.isEating = false;
        }, 1500);
      } else {
        setTimeout(() => {
          SoundFX.burp();
          showSpeechBubble(`Maza aa gaya! ${food.name} tasty tha! 😋`);
          State.isEating = false;
        }, 500);
      }

      State.hunger = Math.min(100, State.hunger + food.hunger);
      addXP(food.xp);
      updateHUD();
    }
  }, 180);
}

document.querySelectorAll('.food-item').forEach(item => {
  item.addEventListener('click', () => {
    feedTom(item.dataset.food);
  });
});

// --- Bathroom: Cleaning Tools ---
let bubblesList = [];

function selectBathTool(toolName) {
  State.activeTool = State.activeTool === toolName ? null : toolName;
  document.querySelectorAll('#tray-bathroom .tray-item').forEach(btn => {
    btn.classList.toggle('active-tool', btn.dataset.tool === State.activeTool);
  });
  if (State.activeTool === 'soap') {
    showSpeechBubble('Tom ke upar sabun lagao! 🧼');
  } else if (State.activeTool === 'shower') {
    showSpeechBubble('Shower se jhaag dho do! 🚿');
  }
}

document.querySelectorAll('#tray-bathroom .tray-item').forEach(btn => {
  btn.addEventListener('click', () => selectBathTool(btn.dataset.tool));
});

characterStage.addEventListener('pointermove', (e) => {
  if (State.activeRoom !== 'bathroom') return;
  const rect = characterStage.getBoundingClientRect();
  const relX = e.clientX - rect.left;
  const relY = e.clientY - rect.top;

  if (State.activeTool === 'soap' && e.buttons > 0) {
    if (Math.random() < 0.4) {
      createSoapBubble(relX, relY);
      SoundFX.splash();
      vibrate(15);
    }
  } else if (State.activeTool === 'shower' && e.buttons > 0) {
    washSoapBubbles(relX, relY);
  }
});

function createSoapBubble(x, y) {
  if (bubblesList.length > 25) return;
  const bubble = document.createElement('div');
  const size = Math.floor(Math.random() * 25) + 20;
  bubble.className = 'bubble';
  bubble.style.width = `${size}px`;
  bubble.style.height = `${size}px`;
  bubble.style.left = `${x - size / 2}px`;
  bubble.style.top = `${y - size / 2}px`;
  characterStage.appendChild(bubble);
  bubblesList.push(bubble);

  bubble.addEventListener('click', () => {
    SoundFX.splash();
    bubble.remove();
    bubblesList = bubblesList.filter(b => b !== bubble);
  });
}

function washSoapBubbles(x, y) {
  if (bubblesList.length > 0) {
    SoundFX.splash();
    vibrate(20);
    bubblesList.forEach(b => {
      b.style.transition = 'transform 0.3s, opacity 0.3s';
      b.style.transform = 'translateY(80px) scale(0)';
      b.style.opacity = '0';
      setTimeout(() => b.remove(), 300);
    });
    bubblesList = [];

    State.hygiene = Math.min(100, State.hygiene + 25);
    addXP(10);
    updateHUD();
    spawnFloatingText(characterStage.offsetLeft + 160, characterStage.offsetTop + 120, '✨ CHAKACHAK! ✨', '#06b6d4');
    showSpeechBubble('Shabaash! Tom ekdum saaf aur chamakdar ho gaya! 🛁✨');
  }
}

// --- Bedroom: Sleep System ---
const bedLamp = document.getElementById('bed-lamp');
const darkOverlay = document.getElementById('bedroom-dark-overlay');
let snoreTimer = null;

function toggleLamp() {
  State.isSleeping = !State.isSleeping;
  vibrate(30);
  SoundFX.boing();

  if (State.isSleeping) {
    bedLamp.classList.add('lamp-off');
    darkOverlay.classList.add('sleep-mode');
    setEyesClosed(true);
    showSpeechBubble('Shhh... Tom so raha hai! 😴💤');

    snoreTimer = setInterval(() => {
      if (!State.isSleeping) return;
      SoundFX.snore();
      spawnFloatingZzz();
      State.energy = Math.min(100, State.energy + 5);
      updateHUD();
      if (State.energy >= 100) {
        showSpeechBubble('Tom ki neend poori ho gayi! Energy 100%! ⚡');
      }
    }, 2800);
  } else {
    bedLamp.classList.remove('lamp-off');
    darkOverlay.classList.remove('sleep-mode');
    setEyesClosed(false);
    clearInterval(snoreTimer);
    showSpeechBubble('Good Morning! Tom uth gaya! 🌞😺');
    SoundFX.meow(540);
  }
}

if (bedLamp) bedLamp.addEventListener('click', toggleLamp);

function spawnFloatingZzz() {
  const el = document.createElement('div');
  el.className = 'floating-zzz';
  el.innerText = 'Zzz...';
  el.style.left = `${characterStage.offsetLeft + 200}px`;
  el.style.top = `${characterStage.offsetTop + 100}px`;
  fxContainer.appendChild(el);
  setTimeout(() => el.remove(), 2500);
}

// --- Props (Punching Bag & Ball) ---
const punchingBag = document.getElementById('punching-bag');
if (punchingBag) {
  punchingBag.addEventListener('click', () => {
    vibrate(40);
    SoundFX.slap();
    punchingBag.style.transform = 'rotate(-30deg)';
    setTimeout(() => {
      punchingBag.style.transform = 'rotate(20deg)';
      setTimeout(() => {
        punchingBag.style.transform = 'rotate(-10deg)';
        setTimeout(() => { punchingBag.style.transform = ''; }, 200);
      }, 200);
    }, 200);
    showSpeechBubble('Dhooom! Zabardast Punch! 🥊');
    State.happy = Math.min(100, State.happy + 5);
    updateHUD();
  });
}

const ballProp = document.getElementById('ball-prop');
if (ballProp) {
  ballProp.addEventListener('click', () => {
    vibrate(30);
    SoundFX.boing();
    ballProp.style.transition = 'transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275)';
    ballProp.style.transform = 'translateY(-140px) scale(1.15)';
    setTimeout(() => {
      ballProp.style.transform = 'translateY(0) scale(1)';
    }, 400);
    State.happy = Math.min(100, State.happy + 4);
    updateHUD();
  });
}

// --- Talking Tom Speech Engine ---
let recognition = null;
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

if (SpeechRecognition) {
  recognition = new SpeechRecognition();
  recognition.continuous = false;
  recognition.interimResults = false;
  recognition.lang = 'hi-IN';

  recognition.onstart = () => {
    micBtn.classList.add('listening');
    showSpeechBubble('Sun raha hu... bolo kuch! 👂🎙️');
  };

  recognition.onresult = (event) => {
    const text = event.results[0][0].transcript;
    micBtn.classList.remove('listening');
    speakLikeTom(text);
  };

  recognition.onerror = () => {
    micBtn.classList.remove('listening');
    showSpeechBubble('Aawaz theek se nahi suni! Dobara bolo.');
  };

  recognition.onend = () => {
    micBtn.classList.remove('listening');
  };
}

function startListening() {
  if (State.isSpeaking) return;
  initAudio();
  if (recognition) {
    try {
      recognition.start();
    } catch (e) {
      recognition.stop();
    }
  } else {
    openTalkModal();
  }
}

if (micBtn) {
  micBtn.addEventListener('click', startListening);
}

function speakLikeTom(text) {
  if (!text) return;
  initAudio();
  showSpeechBubble(text, 5000);

  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.pitch = 1.85; // High Pitch helium voice
    utterance.rate = 1.15;

    const voices = window.speechSynthesis.getVoices();
    const cuteVoice = voices.find(v => v.lang.includes('hi') || v.name.includes('Google') || v.name.includes('Natural'));
    if (cuteVoice) utterance.voice = cuteVoice;

    State.isSpeaking = true;

    const mouthInterval = setInterval(() => {
      if (!State.isSpeaking) {
        clearInterval(mouthInterval);
        setCatMouth(false);
        return;
      }
      setCatMouth(Math.random() > 0.4);
    }, 120);

    utterance.onend = () => {
      State.isSpeaking = false;
      clearInterval(mouthInterval);
      setCatMouth(false);
    };

    utterance.onerror = () => {
      State.isSpeaking = false;
      clearInterval(mouthInterval);
      setCatMouth(false);
    };

    window.speechSynthesis.speak(utterance);
  } else {
    SoundFX.meow(650);
    setTimeout(() => SoundFX.meow(550), 300);
  }
}

// --- Talk Modal ---
const talkModal = document.getElementById('talk-modal');
const talkInput = document.getElementById('talk-input');
const btnSendTalk = document.getElementById('btn-send-talk');

function openTalkModal() {
  talkModal.classList.add('open');
}

function closeTalkModal() {
  talkModal.classList.remove('open');
}

if (btnSendTalk) {
  btnSendTalk.addEventListener('click', () => {
    const val = talkInput.value.trim();
    if (val) {
      closeTalkModal();
      speakLikeTom(val);
      talkInput.value = '';
    }
  });
}

document.querySelectorAll('.phrase-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    closeTalkModal();
    speakLikeTom(chip.innerText);
  });
});

// --- Wardrobe ---
const wardrobeModal = document.getElementById('wardrobe-modal');
const btnOpenWardrobe = document.getElementById('btn-open-wardrobe');

if (btnOpenWardrobe) {
  btnOpenWardrobe.addEventListener('click', () => {
    wardrobeModal.classList.add('open');
  });
}

function equipHat(hatType) {
  State.hat = hatType;
  document.getElementById('hat-party').style.display = hatType === 'party' ? 'block' : 'none';
  document.getElementById('hat-wizard').style.display = hatType === 'wizard' ? 'block' : 'none';
  document.getElementById('hat-crown').style.display = hatType === 'crown' ? 'block' : 'none';
  SoundFX.coin();
  saveProgress();
}

function equipGlasses(glassesType) {
  State.glasses = glassesType;
  document.getElementById('glasses-cool').style.display = glassesType === 'cool' ? 'block' : 'none';
  SoundFX.coin();
  saveProgress();
}

function equipFur(furType) {
  State.fur = furType;
  const colors = {
    grey: { base: '#8c9ba5', shadow: '#6c7982', belly: '#e2e8f0' },
    orange: { base: '#f97316', shadow: '#c2410c', belly: '#ffedd5' },
    black: { base: '#334155', shadow: '#1e293b', belly: '#64748b' },
    white: { base: '#f8fafc', shadow: '#cbd5e1', belly: '#ffffff' }
  };
  const c = colors[furType] || colors.grey;
  document.documentElement.style.setProperty('--fur-color', c.base);
  document.documentElement.style.setProperty('--fur-shadow', c.shadow);
  document.documentElement.style.setProperty('--fur-belly', c.belly);
  SoundFX.coin();
  saveProgress();
}

function equipAccessory(accType) {
  State.accessory = accType;
  document.getElementById('acc-bowtie').style.display = accType === 'bowtie' ? 'block' : 'none';
  SoundFX.coin();
  saveProgress();
}

document.querySelectorAll('.wardrobe-item').forEach(item => {
  item.addEventListener('click', () => {
    const type = item.dataset.type;
    const value = item.dataset.value;
    if (type === 'hat') equipHat(value);
    if (type === 'glasses') equipGlasses(value);
    if (type === 'fur') equipFur(value);
    if (type === 'accessory') equipAccessory(value);

    item.parentElement.querySelectorAll('.wardrobe-item').forEach(el => el.classList.remove('equipped'));
    item.classList.add('equipped');
  });
});

document.querySelectorAll('.btn-close').forEach(btn => {
  btn.addEventListener('click', () => {
    btn.closest('.modal-overlay').classList.remove('open');
  });
});

// --- Mini Game: Canvas Arcade ---
const minigameModal = document.getElementById('minigame-modal');
const btnOpenGame = document.getElementById('btn-open-game');
const canvas = document.getElementById('minigame-canvas');
const ctx = canvas.getContext('2d');
const scoreDisplay = document.getElementById('mg-score');
const livesDisplay = document.getElementById('mg-lives');
const btnStartGame = document.getElementById('btn-start-mg');

let mgLoop = null;
let mgScore = 0;
let mgLives = 3;
let mgBasketX = 140;
let mgItems = [];
let mgIsRunning = false;

if (btnOpenGame) {
  btnOpenGame.addEventListener('click', () => {
    minigameModal.classList.add('open');
  });
}

function startMiniGame() {
  mgScore = 0;
  mgLives = 3;
  mgItems = [];
  mgBasketX = canvas.width / 2 - 25;
  mgIsRunning = true;
  btnStartGame.style.display = 'none';
  scoreDisplay.innerText = `Score: 0`;
  livesDisplay.innerText = `❤️ ❤️ ❤️`;

  if (mgLoop) cancelAnimationFrame(mgLoop);
  runMiniGameLoop();
}

btnStartGame.addEventListener('click', startMiniGame);

canvas.addEventListener('pointermove', (e) => {
  const rect = canvas.getBoundingClientRect();
  const scale = canvas.width / rect.width;
  mgBasketX = (e.clientX - rect.left) * scale - 25;
  mgBasketX = Math.max(0, Math.min(canvas.width - 50, mgBasketX));
});

function runMiniGameLoop() {
  if (!mgIsRunning) return;

  ctx.clearRect(0, 0, canvas.width, canvas.height);

  if (Math.random() < 0.04) {
    const isBomb = Math.random() < 0.25;
    const isCoin = !isBomb && Math.random() < 0.3;
    mgItems.push({
      x: Math.random() * (canvas.width - 30) + 10,
      y: -20,
      speed: Math.random() * 2.5 + 2.5,
      type: isBomb ? 'bomb' : (isCoin ? 'coin' : 'fish'),
      emoji: isBomb ? '💣' : (isCoin ? '🪙' : '🐟')
    });
  }

  // Draw Basket
  ctx.fillStyle = '#f59e0b';
  ctx.beginPath();
  ctx.arc(mgBasketX + 25, canvas.height - 20, 25, 0, Math.PI, false);
  ctx.fill();
  ctx.fillStyle = '#1e293b';
  ctx.font = '20px sans-serif';
  ctx.fillText('🐱', mgBasketX + 15, canvas.height - 22);

  // Update & Draw Items
  for (let i = mgItems.length - 1; i >= 0; i--) {
    const item = mgItems[i];
    item.y += item.speed;

    ctx.font = '24px sans-serif';
    ctx.fillText(item.emoji, item.x - 12, item.y);

    if (item.y >= canvas.height - 45 && item.y <= canvas.height - 15) {
      if (item.x >= mgBasketX - 10 && item.x <= mgBasketX + 55) {
        if (item.type === 'bomb') {
          vibrate(50);
          SoundFX.slap();
          mgLives--;
          livesDisplay.innerText = '❤️ '.repeat(Math.max(0, mgLives));
          if (mgLives <= 0) {
            endMiniGame();
            return;
          }
        } else if (item.type === 'coin') {
          SoundFX.coin();
          mgScore += 25;
          addCoins(2);
        } else {
          SoundFX.crunch();
          mgScore += 10;
        }
        scoreDisplay.innerText = `Score: ${mgScore}`;
        mgItems.splice(i, 1);
        continue;
      }
    }

    if (item.y > canvas.height + 20) {
      mgItems.splice(i, 1);
    }
  }

  mgLoop = requestAnimationFrame(runMiniGameLoop);
}

function endMiniGame() {
  mgIsRunning = false;
  cancelAnimationFrame(mgLoop);
  SoundFX.meow(320);
  ctx.fillStyle = 'rgba(0,0,0,0.6)';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 24px sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('KHEL KHATAM!', canvas.width / 2, canvas.height / 2 - 10);
  ctx.font = '16px sans-serif';
  ctx.fillText(`Final Score: ${mgScore} Pts`, canvas.width / 2, canvas.height / 2 + 20);
  btnStartGame.style.display = 'block';
  btnStartGame.innerText = 'Dobara Khelo 🎮';
  addCoins(Math.floor(mgScore / 10));
}

// --- Persistence ---
function saveProgress() {
  try {
    localStorage.setItem('talking_tom_state', JSON.stringify({
      coins: State.coins,
      level: State.level,
      xp: State.xp,
      hunger: State.hunger,
      hygiene: State.hygiene,
      energy: State.energy,
      happy: State.happy,
      hat: State.hat,
      glasses: State.glasses,
      fur: State.fur,
      accessory: State.accessory
    }));
  } catch (e) {}
}

function loadProgress() {
  try {
    const saved = localStorage.getItem('talking_tom_state');
    if (saved) {
      const data = JSON.parse(saved);
      Object.assign(State, data);
      equipHat(State.hat);
      equipGlasses(State.glasses);
      equipFur(State.fur);
      equipAccessory(State.accessory);
    }
  } catch (e) {}
}

setInterval(() => {
  if (!State.isSleeping) {
    State.hunger = Math.max(0, State.hunger - 0.5);
    State.hygiene = Math.max(0, State.hygiene - 0.4);
    State.energy = Math.max(0, State.energy - 0.6);
    State.happy = Math.max(0, State.happy - 0.5);
    updateHUD();
  }
}, 8000);

// --- Initialization ---
window.addEventListener('DOMContentLoaded', () => {
  loadProgress();
  updateHUD();
  switchRoom('living');
  showSpeechBubble('Namaste dost! Mai hu Tom! 😸❤️');
});
