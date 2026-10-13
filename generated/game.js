/* ============================================================
   CYBER-HEART PROTOCOL: ZOYA & FARDEEN
   Game Logic, Web Audio Synth, Interactive Controls
   ============================================================ */

// ---------------- AUDIO SYNTHESIZER ENGINE ----------------
class SoundEngine {
  constructor() {
    this.ctx = null;
    this.enabled = true;
    this.bgmOscs = [];
  }

  init() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  playTone(freq, type = 'sine', duration = 0.15, gainVal = 0.1) {
    if (!this.enabled) return;
    this.init();
    if (!this.ctx) return;

    try {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();

      osc.type = type;
      osc.frequency.setValueAtTime(freq, this.ctx.currentTime);

      gain.gain.setValueAtTime(gainVal, this.ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, this.ctx.currentTime + duration);

      osc.connect(gain);
      gain.connect(this.ctx.destination);

      osc.start();
      osc.stop(this.ctx.currentTime + duration);
    } catch (e) {
      // Audio fallback
    }
  }

  playClick() {
    this.playTone(800, 'sine', 0.08, 0.08);
  }

  playSuccess() {
    if (!this.enabled) return;
    this.init();
    const notes = [523.25, 659.25, 783.99, 1046.50]; // C5, E5, G5, C6
    notes.forEach((freq, idx) => {
      setTimeout(() => this.playTone(freq, 'triangle', 0.25, 0.12), idx * 80);
    });
  }

  playBuzzer() {
    if (!this.enabled) return;
    this.init();
    this.playTone(180, 'sawtooth', 0.3, 0.15);
    setTimeout(() => this.playTone(130, 'sawtooth', 0.4, 0.15), 120);
  }

  playHeartCatch() {
    this.playTone(987.77, 'sine', 0.15, 0.12);
    setTimeout(() => this.playTone(1318.51, 'sine', 0.2, 0.12), 60);
  }

  playBugHit() {
    this.playTone(220, 'square', 0.18, 0.1);
  }

  playRomanticMelody() {
    if (!this.enabled) return;
    this.init();
    // Sweet polyphonic progression (F - G - Em - Am chord arpeggios)
    const chords = [
      [349.23, 440.00, 523.25], // F major
      [392.00, 493.88, 587.33], // G major
      [329.63, 392.00, 493.88], // E minor
      [440.00, 523.25, 659.25]  // A minor
    ];
    let step = 0;
    const playChordStep = () => {
      if (currentScreen !== 'screen-proposal') return;
      const currentChord = chords[step % chords.length];
      currentChord.forEach((note, i) => {
        setTimeout(() => this.playTone(note, 'sine', 1.2, 0.06), i * 140);
      });
      step++;
      setTimeout(playChordStep, 1800);
    };
    playChordStep();
  }
}

const audio = new SoundEngine();

// ---------------- HAPTIC VIBRATION ----------------
function triggerVibrate(pattern = [40]) {
  if ('vibrate' in navigator) {
    try {
      navigator.vibrate(pattern);
    } catch (e) {}
  }
}

// ---------------- GLOBAL STATE ----------------
let currentScreen = 'screen-intro';
let currentQuestionIndex = 0;
let loveMeterScore = 20;

// Quiz Data with Masti & Cuteness
const quizQuestions = [
  {
    q: "Sawal 1: Fardeen din bhar sabse zyada kya karta hai? 😂",
    options: [
      { text: "Pura din padhai aur serious kaam 📚", type: "funny", roast: "Arey arey itna bada safed jhooth! Fardeen aur itni padhai? 😂 Galat jawab!" },
      { text: "Dosto ke sath gappe marta hai 🍕", type: "funny", roast: "Thoda bohot karta hai, par ye uska main passion nahi hai madam! 😜" },
      { text: "Sirf aur sirf Zoya ke khayalo me khoya rehta hai 🙈💖", type: "correct", roast: "BINGO! 100% Sach! Fardeen ke har second me sirf tum hi tum ho! ✨" },
      { text: "Khaata hai aur so jata hai 😴", type: "funny", roast: "Ye toh tumhara routine lag raha hai! Fardeen ka nahi 😂" }
    ]
  },
  {
    q: "Sawal 2: Zoya ka sabse dangerous weapon kya hai jisse Fardeen ghayal ho jata hai? ⚔️",
    options: [
      { text: "Uski killer smile aur cute nakhre 🥺✨", type: "correct", roast: "Sachme! Is weapon ka koi antivirus pure internet par nahi bana! ❤️" },
      { text: "Ek baar 'Hmm' bolkar ignore karna 💀", type: "funny", roast: "Critical Damage 9999 HP! Fardeen ka dimaag hil jata hai isse 😂" },
      { text: "Uski gusse wali aankhen 😤", type: "funny", roast: "Gusse me bhi tum cute hi lagti ho, darr bilkul nahi lagta! 😜" },
      { text: "Baatein ghuma-fira kar bolna 🌀", type: "funny", roast: "Hehehe, par Fardeen fir bhi pyaar se sunta rehta hai! 💖" }
    ]
  },
  {
    q: "Sawal 3: Agar Fardeen aur Zoya me fight ho jaye, toh galti kiski hogi? 🥊",
    options: [
      { text: "Obviously Fardeen ki (Chahe jo ho jaye) 👑", type: "correct", roast: "Universal Truth Rule #1: Zoya is ALWAYS right! Fardeen bina lade sorry bol dega! 😂🤝" },
      { text: "Kisi ki nahi, dono milkar ice-cream khayenge 🍦", type: "correct", roast: "Super cute! Treat Fardeen ki taraf se pakki rahi! 😋💖" },
      { text: "Zoya ki galti hogi thodi si 🤏", type: "funny", roast: "Arey system glitch! Zoya ki galti ho hi nahi sakti, Fardeen hi maafi mangega! 🤣" }
    ]
  }
];

// ---------------- DOM ELEMENTS ----------------
const soundBtn = document.getElementById('sound-btn');
const soundIcon = document.getElementById('sound-icon');

// Sound Toggle Button
soundBtn.addEventListener('click', () => {
  audio.enabled = !audio.enabled;
  soundIcon.textContent = audio.enabled ? '🔊' : '🔇';
  if (audio.enabled) audio.playClick();
});

// Screen Switcher
function switchScreen(screenId) {
  document.querySelectorAll('.game-screen').forEach(el => el.classList.remove('active'));
  const target = document.getElementById(screenId);
  if (target) {
    target.classList.add('active');
    currentScreen = screenId;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}

// ---------------- SCREEN 1: VERIFICATION ----------------
function verifyIdentity(type) {
  audio.init();
  const dialogue = document.getElementById('intro-dialogue');
  
  if (type === 'alien') {
    audio.playBuzzer();
    triggerVibrate([60, 40, 60]);
    dialogue.innerHTML = "Hahaha! Alien ho tab bhi Fardeen tumhara hi dimaag khayega! 😂 Chalo ab chup-chaap game shuru karo!";
    setTimeout(() => {
      startQuiz();
    }, 1500);
  } else {
    audio.playSuccess();
    triggerVibrate([80]);
    dialogue.innerHTML = "Access Granted! 💖 Welcome Zoya! Fardeen ne ye secret game khas tumhare chehre par muskurahat laane ke liye banaya hai!";
    setTimeout(() => {
      startQuiz();
    }, 1200);
  }
}

// ---------------- SCREEN 2: QUIZ LOGIC ----------------
function startQuiz() {
  currentQuestionIndex = 0;
  loveMeterScore = 25;
  switchScreen('screen-quiz');
  loadQuestion();
}

function loadQuestion() {
  const qData = quizQuestions[currentQuestionIndex];
  document.getElementById('quiz-progress').textContent = `QUESTION ${currentQuestionIndex + 1} / ${quizQuestions.length}`;
  document.getElementById('love-meter-fill').style.width = `${loveMeterScore}%`;
  document.getElementById('question-text').textContent = qData.q;
  
  const optionsContainer = document.getElementById('options-container');
  optionsContainer.innerHTML = '';
  document.getElementById('feedback-box').classList.add('hidden');

  qData.options.forEach((opt, idx) => {
    const btn = document.createElement('button');
    btn.className = 'option-btn';
    btn.innerHTML = `<span class="opt-bullet">✨</span><span>${opt.text}</span>`;
    btn.onclick = () => handleAnswer(opt, btn);
    optionsContainer.appendChild(btn);
  });
}

function handleAnswer(option, btnElement) {
  audio.init();
  const feedbackBox = document.getElementById('feedback-box');
  const feedbackEmoji = document.getElementById('feedback-emoji');
  const feedbackText = document.getElementById('feedback-text');

  // Disable all option buttons
  document.querySelectorAll('.option-btn').forEach(b => b.style.pointerEvents = 'none');

  if (option.type === 'correct') {
    audio.playSuccess();
    triggerVibrate([60, 50, 100]);
    btnElement.classList.add('selected-correct');
    feedbackEmoji.textContent = '🥰';
    loveMeterScore = Math.min(100, loveMeterScore + 30);
  } else {
    audio.playBuzzer();
    triggerVibrate([80]);
    btnElement.classList.add('selected-funny');
    feedbackEmoji.textContent = '😂';
    loveMeterScore = Math.min(100, loveMeterScore + 15);
  }

  feedbackText.textContent = option.roast;
  feedbackBox.classList.remove('hidden');
  document.getElementById('love-meter-fill').style.width = `${loveMeterScore}%`;
}

function nextQuestion() {
  audio.playClick();
  currentQuestionIndex++;
  if (currentQuestionIndex < quizQuestions.length) {
    loadQuestion();
  } else {
    // Move to Scanner
    switchScreen('screen-scanner');
    initScanner();
  }
}

// ---------------- SCREEN 3: LOVE SCANNER ----------------
function initScanner() {
  const zone = document.getElementById('scanner-zone');
  const counter = document.getElementById('scan-counter');
  const hint = document.getElementById('scan-hint');
  const result = document.getElementById('scan-result');
  let scanInterval = null;
  let progress = 0;

  const startScan = (e) => {
    if (e) e.preventDefault();
    if (progress >= 100) return;
    audio.init();
    audio.playTone(400, 'sine', 0.1, 0.05);
    zone.classList.add('scanning');
    hint.textContent = "Scanning Crush Frequency... 💓";

    scanInterval = setInterval(() => {
      progress += 4;
      counter.textContent = `${progress}%`;
      triggerVibrate([20]);
      audio.playTone(300 + progress * 8, 'sine', 0.04, 0.04);

      if (progress >= 100) {
        clearInterval(scanInterval);
        zone.classList.remove('scanning');
        counter.textContent = "999.9%";
        hint.textContent = "SCAN COMPLETE!";
        audio.playSuccess();
        triggerVibrate([100, 50, 150]);
        result.classList.remove('hidden');
      }
    }, 60);
  };

  const stopScan = () => {
    if (progress < 100) {
      clearInterval(scanInterval);
      zone.classList.remove('scanning');
      hint.textContent = "Don't let go! Tap & hold again!";
    }
  };

  zone.onmousedown = startScan;
  zone.onmouseup = stopScan;
  zone.ontouchstart = startScan;
  zone.ontouchend = stopScan;
}

function goToMiniGame() {
  audio.playClick();
  switchScreen('screen-game');
}

// ---------------- SCREEN 4: MINI GAME (CATCH HEARTS) ----------------
let gameScore = 0;
let gameTimer = 18;
let gameLoopInterval = null;
let itemSpawnInterval = null;
let isGameRunning = false;

function startCatchGame() {
  audio.init();
  audio.playClick();
  document.getElementById('game-start-overlay').style.display = 'none';
  gameScore = 0;
  gameTimer = 18;
  isGameRunning = true;
  document.getElementById('game-score').textContent = gameScore;
  document.getElementById('game-timer').textContent = `${gameTimer}s`;

  const arena = document.getElementById('game-arena');
  const basket = document.getElementById('basket');
  const itemsLayer = document.getElementById('items-layer');
  itemsLayer.innerHTML = '';

  // Basket Touch & Mouse Control
  let basketX = arena.clientWidth / 2;

  const updateBasketPos = (clientX) => {
    const rect = arena.getBoundingClientRect();
    let relativeX = clientX - rect.left;
    relativeX = Math.max(34, Math.min(arena.clientWidth - 34, relativeX));
    basket.style.left = `${relativeX}px`;
    basketX = relativeX;
  };

  arena.ontouchmove = (e) => {
    e.preventDefault();
    if (e.touches[0]) updateBasketPos(e.touches[0].clientX);
  };
  arena.onmousemove = (e) => {
    updateBasketPos(e.clientX);
  };

  // Spawn Falling Items
  const items = [
    { emoji: '💖', pts: 10, type: 'heart' },
    { emoji: '💕', pts: 10, type: 'heart' },
    { emoji: '⭐', pts: 15, type: 'star' },
    { emoji: '💌', pts: 20, type: 'letter' },
    { emoji: '🐛', pts: -5, type: 'bug' } // Overthinking bug
  ];

  itemSpawnInterval = setInterval(() => {
    if (!isGameRunning) return;
    const randomItem = items[Math.floor(Math.random() * items.length)];
    const itemEl = document.createElement('div');
    itemEl.className = 'game-item';
    itemEl.textContent = randomItem.emoji;
    itemEl.dataset.type = randomItem.type;
    itemEl.dataset.pts = randomItem.pts;

    const startLeft = Math.random() * (arena.clientWidth - 40) + 20;
    itemEl.style.left = `${startLeft}px`;
    itemEl.style.top = '-40px';
    itemsLayer.appendChild(itemEl);

    // Fall animation
    let topY = -40;
    const speed = 3.2 + Math.random() * 2.5;

    const fall = setInterval(() => {
      if (!isGameRunning) {
        clearInterval(fall);
        itemEl.remove();
        return;
      }
      topY += speed;
      itemEl.style.top = `${topY}px`;

      // Collision Detection with basket
      if (topY >= arena.clientHeight - 65 && topY <= arena.clientHeight - 15) {
        const itemLeft = parseFloat(itemEl.style.left);
        if (Math.abs(itemLeft - basketX) < 45) {
          // Caught!
          clearInterval(fall);
          itemEl.remove();
          const pts = parseInt(randomItem.pts);
          gameScore = Math.max(0, gameScore + pts);
          document.getElementById('game-score').textContent = gameScore;

          if (pts > 0) {
            audio.playHeartCatch();
            triggerVibrate([30]);
            basket.querySelector('.basket-face').textContent = '😍';
          } else {
            audio.playBugHit();
            triggerVibrate([70]);
            basket.querySelector('.basket-face').textContent = '😵';
          }
          setTimeout(() => {
            if (basket) basket.querySelector('.basket-face').textContent = '🥰';
          }, 300);
        }
      }

      if (topY > arena.clientHeight) {
        clearInterval(fall);
        itemEl.remove();
      }
    }, 20);

  }, 600);

  // Countdown timer
  gameLoopInterval = setInterval(() => {
    gameTimer--;
    document.getElementById('game-timer').textContent = `${gameTimer}s`;

    if (gameTimer <= 0 || gameScore >= 60) {
      endCatchGame();
    }
  }, 1000);
}

function endCatchGame() {
  isGameRunning = false;
  clearInterval(gameLoopInterval);
  clearInterval(itemSpawnInterval);
  audio.playSuccess();
  triggerVibrate([80, 50, 120]);

  setTimeout(() => {
    switchScreen('screen-cipher');
  }, 700);
}

// ---------------- SCREEN 5: CIPHER UNLOCK ----------------
let enteredKeys = [];
const correctCode = ['Z', 'O', 'Y', 'A'];

function pressKey(letter) {
  audio.init();
  if (enteredKeys.length < 4) {
    audio.playClick();
    triggerVibrate([30]);
    enteredKeys.push(letter);
    updateCipherSlots();

    if (enteredKeys.length === 4) {
      checkCipher();
    }
  }
}

function clearKeys() {
  audio.playClick();
  enteredKeys = [];
  updateCipherSlots();
  document.getElementById('cipher-message').textContent = "Sahi letters tap karo...";
}

function updateCipherSlots() {
  for (let i = 0; i < 4; i++) {
    const slot = document.getElementById(`slot-${i}`);
    slot.textContent = enteredKeys[i] ? enteredKeys[i] : '_';
  }
}

function checkCipher() {
  const isMatch = enteredKeys.every((val, index) => val === correctCode[index]);
  const msg = document.getElementById('cipher-message');

  if (isMatch) {
    audio.playSuccess();
    triggerVibrate([100, 60, 150]);
    msg.textContent = "ACCESS GRANTED! Fardeen ka dil unlock ho gaya! 💖🔓";
    msg.style.color = "var(--neon-cyan)";
    setTimeout(() => {
      switchScreen('screen-proposal');
      initProposalScreen();
    }, 1200);
  } else {
    audio.playBuzzer();
    triggerVibrate([80, 50, 80]);
    msg.textContent = "Oops! Galat password! Hint: Z-O-Y-A 😜";
    msg.style.color = "var(--neon-pink)";
    setTimeout(() => {
      clearKeys();
    }, 1000);
  }
}

// ---------------- SCREEN 6: THE PROPOSAL & KHAT ----------------
function initProposalScreen() {
  initFxCanvas();
}

function openLoveLetter() {
  audio.init();
  audio.playSuccess();
  triggerVibrate([100]);
  document.getElementById('envelope-sealed').style.display = 'none';
  document.getElementById('letter-content').classList.remove('hidden');
  audio.playRomanticMelody();
  setupRunawayButton();
}

// The Hilarious Runaway "Sochungi 😏" Button
function setupRunawayButton() {
  const noBtn = document.getElementById('no-btn');
  const noBtnText = document.getElementById('no-btn-text');
  const funnyLines = [
    "Arey pakad ke dikhao! 😂",
    "Fardeen ko No bolna mana hai! 😜",
    "Button bhaag gaya lol! 🏃‍♂️💨",
    "Itna bhav mat khao Zoya! 🤣",
    "Pyaar se YES dabao na! 💖",
    "Error: Rejection module crashed! 🤖",
    "Haath me nahi aaunga! 😝"
  ];
  let dodgeCount = 0;

  const dodge = (e) => {
    if (e) e.preventDefault();
    audio.playTone(600 + Math.random() * 400, 'sine', 0.08, 0.08);
    triggerVibrate([30]);

    dodgeCount++;
    noBtnText.textContent = funnyLines[dodgeCount % funnyLines.length];

    // Calculate safe random coordinates inside screen
    const maxOffset = 110;
    const randX = (Math.random() - 0.5) * maxOffset * 1.8;
    const randY = (Math.random() - 0.5) * maxOffset;

    noBtn.style.transform = `translate(${randX}px, ${randY}px) scale(0.95)`;
  };

  noBtn.onmouseenter = dodge;
  noBtn.ontouchstart = dodge;
  noBtn.onclick = dodge;
}

// Accept Proposal (YES!)
function acceptProposal() {
  audio.init();
  audio.playSuccess();
  triggerVibrate([150, 80, 200, 100, 300]);

  document.getElementById('letter-content').classList.add('hidden');
  document.getElementById('celebration-box').classList.remove('hidden');

  // Launch Super Confetti & Fireworks!
  launchCelebrationParticles();
}

// ---------------- CONFETTI & FIREWORKS ENGINE ----------------
let particles = [];
let animFrameId = null;

function initFxCanvas() {
  const canvas = document.getElementById('fx-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

  const resize = () => {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  };
  resize();
  window.addEventListener('resize', resize);
}

function launchCelebrationParticles() {
  const canvas = document.getElementById('fx-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  particles = [];

  const colors = ['#ff2d75', '#00f0ff', '#ffd166', '#ffffff', '#b5179e', '#ff70a6'];

  // Spawn 150 particles
  for (let i = 0; i < 160; i++) {
    particles.push({
      x: canvas.width / 2,
      y: canvas.height / 2 + 50,
      vx: (Math.random() - 0.5) * 16,
      vy: (Math.random() - 0.8) * 18,
      size: Math.random() * 8 + 4,
      color: colors[Math.floor(Math.random() * colors.length)],
      alpha: 1,
      decay: Math.random() * 0.008 + 0.004,
      isHeart: Math.random() > 0.4
    });
  }

  const loop = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    particles.forEach((p, idx) => {
      p.x += p.vx;
      p.y += p.vy;
      p.vy += 0.28; // gravity
      p.alpha -= p.decay;

      if (p.alpha <= 0) {
        particles.splice(idx, 1);
        return;
      }

      ctx.save();
      ctx.globalAlpha = p.alpha;
      ctx.fillStyle = p.color;

      if (p.isHeart) {
        // Draw tiny glowing heart
        ctx.font = `${p.size * 2}px sans-serif`;
        ctx.fillText('💖', p.x, p.y);
      } else {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    });

    if (particles.length > 0) {
      animFrameId = requestAnimationFrame(loop);
    }
  };

  loop();
}

// ---------------- SHARE & RESTART ----------------
function shareWithFardeen() {
  const msg = encodeURIComponent("Fardeen! Maine tumhara Cyber Game khela aur mera answer YES hai! 💖💍 Tum sach me bohot cute ho!");
  window.open(`https://api.whatsapp.com/send?text=${msg}`, '_blank');
}

function restartGame() {
  audio.playClick();
  currentQuestionIndex = 0;
  enteredKeys = [];
  document.getElementById('celebration-box').classList.add('hidden');
  document.getElementById('letter-content').classList.add('hidden');
  document.getElementById('envelope-sealed').style.display = 'block';
  document.getElementById('game-start-overlay').style.display = 'flex';
  switchScreen('screen-intro');
}

// Background Twinkling Cyber Stars
function createCyberStars() {
  const container = document.getElementById('stars-container');
  if (!container) return;
  for (let i = 0; i < 40; i++) {
    const star = document.createElement('div');
    star.style.position = 'absolute';
    star.style.width = `${Math.random() * 3 + 1}px`;
    star.style.height = star.style.width;
    star.style.background = Math.random() > 0.5 ? '#00f0ff' : '#ff2d75';
    star.style.borderRadius = '50%';
    star.style.top = `${Math.random() * 100}%`;
    star.style.left = `${Math.random() * 100}%`;
    star.style.opacity = Math.random() * 0.7 + 0.3;
    star.style.boxShadow = `0 0 6px ${star.style.background}`;
    container.appendChild(star);
  }
}

window.addEventListener('DOMContentLoaded', () => {
  createCyberStars();
});
