// --- Audio Synthesis via Web Audio API (Zero external audio files required) ---
class SoundController {
  constructor() {
    this.ctx = null;
    this.muted = false;
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

  toggleMute() {
    this.muted = !this.muted;
    return this.muted;
  }

  playCoin() {
    if (this.muted || !this.ctx) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    
    osc.type = 'sine';
    osc.frequency.setValueAtTime(800, now);
    osc.frequency.exponentialRampToValueAtTime(1400, now + 0.12);
    
    gain.gain.setValueAtTime(0.3, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);
    
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    
    osc.start(now);
    osc.stop(now + 0.12);
  }

  playShield() {
    if (this.muted || !this.ctx) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(350, now);
    osc.frequency.linearRampToValueAtTime(700, now + 0.25);
    
    gain.gain.setValueAtTime(0.3, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.3);
    
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    
    osc.start(now);
    osc.stop(now + 0.3);
  }

  playExplosion() {
    if (this.muted || !this.ctx) return;
    const now = this.ctx.currentTime;
    const bufferSize = this.ctx.sampleRate * 0.4;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = Math.random() * 2 - 1;
    }

    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(700, now);
    filter.frequency.linearRampToValueAtTime(50, now + 0.4);

    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.5, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.4);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.ctx.destination);

    noise.start(now);
    noise.stop(now + 0.4);
  }
}

// --- Main Game Setup ---
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');

const scoreEl = document.getElementById('scoreDisplay');
const startScreen = document.getElementById('startScreen');
const gameOverScreen = document.getElementById('gameOverScreen');
const startBtn = document.getElementById('startBtn');
const restartBtn = document.getElementById('restartBtn');
const soundBtn = document.getElementById('soundBtn');
const finalScoreEl = document.getElementById('finalScore');
const bestScoreEl = document.getElementById('bestScore');

const sound = new SoundController();

let width = window.innerWidth;
let height = window.innerHeight;
let highscore = parseInt(localStorage.getItem('astro_dodge_hs') || '0', 10);

function resizeCanvas() {
  width = window.innerWidth;
  height = window.innerHeight;
  canvas.width = width;
  canvas.height = height;
}
window.addEventListener('resize', resizeCanvas);
resizeCanvas();

// Game State
let isPlaying = false;
let score = 0;
let distance = 0;
let speed = 4;
let screenShake = 0;

// Entities
let player;
let stars = [];
let obstacles = [];
let pickups = [];
let particles = [];
let popups = [];

// Starfield initialization
function initStars() {
  stars = [];
  for (let i = 0; i < 80; i++) {
    stars.push({
      x: Math.random() * width,
      y: Math.random() * height,
      size: Math.random() * 2 + 0.5,
      speed: Math.random() * 1.5 + 0.5,
      alpha: Math.random() * 0.8 + 0.2
    });
  }
}

// Player Object
class Player {
  constructor() {
    this.width = 44;
    this.height = 54;
    this.x = width / 2;
    this.y = height - 120;
    this.targetX = this.x;
    this.targetY = this.y;
    this.shield = false;
    this.shieldTimer = 0;
    this.bankAngle = 0;
  }

  update() {
    // Smooth lerp movement toward target touch/mouse position
    const dx = this.targetX - this.x;
    this.x += dx * 0.2;

    const dy = this.targetY - this.y;
    this.y += dy * 0.2;

    // Boundary constraints
    this.x = Math.max(this.width / 2, Math.min(width - this.width / 2, this.x));
    this.y = Math.max(this.height, Math.min(height - 40, this.y));

    // Dynamic banking angle based on horizontal speed
    this.bankAngle = (dx * 0.05);
    this.bankAngle = Math.max(-0.4, Math.min(0.4, this.bankAngle));

    // Thruster engine particles
    if (Math.random() > 0.1) {
      particles.push({
        x: this.x + (Math.random() * 8 - 4),
        y: this.y + 24,
        vx: (Math.random() - 0.5) * 1.5,
        vy: Math.random() * 3 + 4,
        size: Math.random() * 4 + 3,
        color: Math.random() > 0.4 ? '#00f0ff' : '#ff0077',
        life: 1.0,
        decay: 0.05
      });
    }

    if (this.shield) {
      this.shieldTimer -= 1 / 60;
      if (this.shieldTimer <= 0) {
        this.shield = false;
      }
    }
  }

  draw() {
    ctx.save();
    ctx.translate(this.x, this.y);
    ctx.rotate(this.bankAngle);

    // Glowing Neon Shield if active
    if (this.shield) {
      ctx.save();
      ctx.beginPath();
      ctx.arc(0, 0, 36 + Math.sin(Date.now() * 0.01) * 3, 0, Math.PI * 2);
      ctx.strokeStyle = '#00f0ff';
      ctx.lineWidth = 3;
      ctx.shadowColor = '#00f0ff';
      ctx.shadowBlur = 15;
      ctx.fillStyle = 'rgba(0, 240, 255, 0.15)';
      ctx.fill();
      ctx.stroke();
      ctx.restore();
    }

    // Spaceship Body (Futuristic Fighter)
    ctx.shadowColor = '#00f0ff';
    ctx.shadowBlur = 12;

    // Wings
    ctx.fillStyle = '#0f274a';
    ctx.beginPath();
    ctx.moveTo(0, -26);
    ctx.lineTo(24, 20);
    ctx.lineTo(12, 16);
    ctx.lineTo(0, 22);
    ctx.lineTo(-12, 16);
    ctx.lineTo(-24, 20);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#00f0ff';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Central Fuselage
    ctx.fillStyle = '#1e4880';
    ctx.beginPath();
    ctx.moveTo(0, -28);
    ctx.lineTo(8, 10);
    ctx.lineTo(0, 16);
    ctx.lineTo(-8, 10);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#7be7ff';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // Glowing Cockpit
    ctx.fillStyle = '#ff0077';
    ctx.shadowColor = '#ff0077';
    ctx.shadowBlur = 10;
    ctx.beginPath();
    ctx.ellipse(0, -4, 4, 10, 0, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }
}

// Asteroid / Enemy Class
class Obstacle {
  constructor() {
    this.size = Math.random() * 24 + 20;
    this.x = Math.random() * (width - this.size * 2) + this.size;
    this.y = -this.size - 20;
    this.vy = speed + Math.random() * 2;
    this.vx = (Math.random() - 0.5) * 1.5;
    this.angle = 0;
    this.rotSpeed = (Math.random() - 0.5) * 0.05;
    this.points = [];
    
    // Procedural Asteroid Shape
    const numPoints = 8;
    for (let i = 0; i < numPoints; i++) {
      const a = (i / numPoints) * Math.PI * 2;
      const r = this.size * (0.8 + Math.random() * 0.4);
      this.points.push({ x: Math.cos(a) * r, y: Math.sin(a) * r });
    }
  }

  update() {
    this.y += this.vy;
    this.x += this.vx;
    this.angle += this.rotSpeed;
  }

  draw() {
    ctx.save();
    ctx.translate(this.x, this.y);
    ctx.rotate(this.angle);

    ctx.fillStyle = '#221c2e';
    ctx.strokeStyle = '#ff4d6d';
    ctx.lineWidth = 2.5;
    ctx.shadowColor = '#ff2a55';
    ctx.shadowBlur = 10;

    ctx.beginPath();
    ctx.moveTo(this.points[0].x, this.points[0].y);
    for (let i = 1; i < this.points.length; i++) {
      ctx.lineTo(this.points[i].x, this.points[i].y);
    }
    ctx.closePath();
    ctx.fill();
    ctx.stroke();

    // Inner detail
    ctx.fillStyle = 'rgba(255, 77, 109, 0.2)';
    ctx.beginPath();
    ctx.arc(4, -4, this.size * 0.25, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }
}

// Collectible Gem / Shield
class Pickup {
  constructor() {
    this.type = Math.random() < 0.2 ? 'shield' : 'crystal';
    this.size = 18;
    this.x = Math.random() * (width - 60) + 30;
    this.y = -30;
    this.vy = speed * 0.85;
    this.pulse = 0;
  }

  update() {
    this.y += this.vy;
    this.pulse += 0.08;
  }

  draw() {
    ctx.save();
    ctx.translate(this.x, this.y);
    const scale = 1 + Math.sin(this.pulse) * 0.15;
    ctx.scale(scale, scale);

    if (this.type === 'shield') {
      ctx.shadowColor = '#00f0ff';
      ctx.shadowBlur = 14;
      ctx.strokeStyle = '#00f0ff';
      ctx.lineWidth = 2.5;
      ctx.fillStyle = 'rgba(0, 240, 255, 0.4)';
      ctx.beginPath();
      ctx.arc(0, 0, 14, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();

      // Shield icon
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 12px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('🛡️', 0, 0);
    } else {
      // Crystal diamond
      ctx.shadowColor = '#ffe600';
      ctx.shadowBlur = 14;
      ctx.fillStyle = '#ffe600';
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(0, -14);
      ctx.lineTo(12, 0);
      ctx.lineTo(0, 14);
      ctx.lineTo(-12, 0);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
    }

    ctx.restore();
  }
}

// Floating Popup Text
function addPopup(text, x, y, color = '#00f0ff') {
  popups.push({
    text,
    x,
    y,
    vy: -2,
    alpha: 1.0,
    color
  });
}

// Particle Explosions
function spawnExplosion(x, y, color = '#ff0055', count = 25) {
  for (let i = 0; i < count; i++) {
    const angle = Math.random() * Math.PI * 2;
    const mag = Math.random() * 6 + 1.5;
    particles.push({
      x,
      y,
      vx: Math.cos(angle) * mag,
      vy: Math.sin(angle) * mag,
      size: Math.random() * 5 + 2,
      color,
      life: 1.0,
      decay: Math.random() * 0.04 + 0.02
    });
  }
}

// --- Input Handling (Touch + Mouse + Keyboard) ---
let isTouching = false;

function setTargetPosition(clientX, clientY) {
  if (!player) return;
  const rect = canvas.getBoundingClientRect();
  player.targetX = clientX - rect.left;
  // Offset slightly above finger so finger does not cover ship
  player.targetY = clientY - rect.top - 30;
}

// Touch events
window.addEventListener('touchstart', (e) => {
  sound.init();
  if (!isPlaying) return;
  isTouching = true;
  const touch = e.touches[0];
  setTargetPosition(touch.clientX, touch.clientY);
}, { passive: false });

window.addEventListener('touchmove', (e) => {
  if (!isPlaying || !isTouching) return;
  const touch = e.touches[0];
  setTargetPosition(touch.clientX, touch.clientY);
  e.preventDefault();
}, { passive: false });

window.addEventListener('touchend', () => {
  isTouching = false;
});

// Mouse events
window.addEventListener('mousedown', (e) => {
  sound.init();
  if (!isPlaying) return;
  setTargetPosition(e.clientX, e.clientY);
});

window.addEventListener('mousemove', (e) => {
  if (!isPlaying) return;
  if (e.buttons === 1) {
    setTargetPosition(e.clientX, e.clientY);
  }
});

// Keyboard support
const keys = {};
window.addEventListener('keydown', (e) => {
  keys[e.key] = true;
  sound.init();
});
window.addEventListener('keyup', (e) => {
  keys[e.key] = false;
});

function handleKeyboardInput() {
  if (!player || !isPlaying) return;
  const moveSpeed = 8;
  if (keys['ArrowLeft'] || keys['a'] || keys['A']) player.targetX -= moveSpeed;
  if (keys['ArrowRight'] || keys['d'] || keys['D']) player.targetX += moveSpeed;
  if (keys['ArrowUp'] || keys['w'] || keys['W']) player.targetY -= moveSpeed;
  if (keys['ArrowDown'] || keys['s'] || keys['S']) player.targetY += moveSpeed;
}

// Sound toggle
soundBtn.addEventListener('click', () => {
  sound.init();
  const isMuted = sound.toggleMute();
  soundBtn.textContent = isMuted ? '🔇' : '🔊';
});

// Game Cycle
let spawnTimer = 0;
let pickupTimer = 0;

function resetGame() {
  score = 0;
  distance = 0;
  speed = 4;
  screenShake = 0;
  obstacles = [];
  pickups = [];
  particles = [];
  popups = [];
  player = new Player();
  initStars();
  scoreEl.textContent = `SCORE: 0`;
}

function startGame() {
  sound.init();
  resetGame();
  startScreen.classList.add('hidden');
  gameOverScreen.classList.add('hidden');
  isPlaying = true;
}

function triggerGameOver() {
  isPlaying = false;
  sound.playExplosion();
  if (navigator.vibrate) navigator.vibrate([100, 50, 200]);

  if (score > highscore) {
    highscore = score;
    localStorage.setItem('astro_dodge_hs', highscore.toString());
  }

  finalScoreEl.textContent = score.toString();
  bestScoreEl.textContent = highscore.toString();
  gameOverScreen.classList.remove('hidden');
}

startBtn.addEventListener('click', startGame);
restartBtn.addEventListener('click', startGame);

// Animation Loop
let lastTime = performance.now();

function gameLoop(now) {
  requestAnimationFrame(gameLoop);

  const dt = Math.min((now - lastTime) / 1000, 0.1);
  lastTime = now;

  // Screen shake handling
  ctx.save();
  if (screenShake > 0) {
    const sx = (Math.random() - 0.5) * screenShake;
    const sy = (Math.random() - 0.5) * screenShake;
    ctx.translate(sx, sy);
    screenShake *= 0.9;
    if (screenShake < 0.5) screenShake = 0;
  }

  // Clear Canvas with subtle motion trail
  ctx.fillStyle = '#050714';
  ctx.fillRect(0, 0, width, height);

  // Update & Draw Starfield
  for (let s of stars) {
    s.y += s.speed * (isPlaying ? (speed / 3) : 1);
    if (s.y > height) {
      s.y = 0;
      s.x = Math.random() * width;
    }
    ctx.fillStyle = `rgba(180, 220, 255, ${s.alpha})`;
    ctx.fillRect(s.x, s.y, s.size, s.size * 1.5);
  }

  if (isPlaying) {
    handleKeyboardInput();

    // Distance and score progression
    distance += dt * 10;
    score = Math.floor(distance);
    scoreEl.textContent = `SCORE: ${score}`;

    // Dynamic speed ramp-up
    speed = 4 + Math.min(10, score / 200);

    // Spawning Obstacles
    spawnTimer += dt;
    const spawnInterval = Math.max(0.5, 1.4 - (score / 1000));
    if (spawnTimer >= spawnInterval) {
      obstacles.push(new Obstacle());
      spawnTimer = 0;
    }

    // Spawning Pickups
    pickupTimer += dt;
    if (pickupTimer >= 2.5) {
      pickups.push(new Pickup());
      pickupTimer = 0;
    }

    player.update();
  }

  // Update & Draw Pickups
  for (let i = pickups.length - 1; i >= 0; i--) {
    const p = pickups[i];
    p.update();
    p.draw();

    // Pickup collision with player
    if (isPlaying) {
      const dist = Math.hypot(p.x - player.x, p.y - player.y);
      if (dist < p.size + player.width * 0.45) {
        if (p.type === 'shield') {
          player.shield = true;
          player.shieldTimer = 8; // 8 seconds of shield
          sound.playShield();
          if (navigator.vibrate) navigator.vibrate(50);
          addPopup('SHIELD ACTIVE!', p.x, p.y, '#00f0ff');
        } else {
          score += 100;
          distance += 100;
          sound.playCoin();
          if (navigator.vibrate) navigator.vibrate(30);
          addPopup('+100', p.x, p.y, '#ffe600');
        }
        spawnExplosion(p.x, p.y, p.type === 'shield' ? '#00f0ff' : '#ffe600', 12);
        pickups.splice(i, 1);
        continue;
      }
    }

    if (p.y > height + 40) {
      pickups.splice(i, 1);
    }
  }

  // Update & Draw Obstacles
  for (let i = obstacles.length - 1; i >= 0; i--) {
    const obs = obstacles[i];
    obs.update();
    obs.draw();

    // Collision Check
    if (isPlaying) {
      const dist = Math.hypot(obs.x - player.x, obs.y - player.y);
      if (dist < obs.size * 0.8 + player.width * 0.4) {
        if (player.shield) {
          // Shield absorbs impact
          player.shield = false;
          screenShake = 12;
          sound.playExplosion();
          if (navigator.vibrate) navigator.vibrate(80);
          spawnExplosion(obs.x, obs.y, '#00f0ff', 20);
          addPopup('SHIELD BROKEN!', player.x, player.y - 20, '#ff0077');
          obstacles.splice(i, 1);
          continue;
        } else {
          // Ship destroyed
          screenShake = 22;
          spawnExplosion(player.x, player.y, '#ff0055', 40);
          spawnExplosion(player.x, player.y, '#00f0ff', 25);
          triggerGameOver();
          break;
        }
      }
    }

    if (obs.y > height + 60) {
      obstacles.splice(i, 1);
    }
  }

  // Draw Player
  if (isPlaying) {
    player.draw();
  }

  // Update & Draw Particles
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.x += p.vx;
    p.y += p.vy;
    p.life -= p.decay;

    if (p.life <= 0) {
      particles.splice(i, 1);
      continue;
    }

    ctx.save();
    ctx.globalAlpha = p.life;
    ctx.fillStyle = p.color;
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size * p.life, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  // Update & Draw Floating Popups
  for (let i = popups.length - 1; i >= 0; i--) {
    const pop = popups[i];
    pop.y += pop.vy;
    pop.alpha -= 0.02;

    if (pop.alpha <= 0) {
      popups.splice(i, 1);
      continue;
    }

    ctx.save();
    ctx.globalAlpha = pop.alpha;
    ctx.fillStyle = pop.color;
    ctx.font = 'bold 16px sans-serif';
    ctx.textAlign = 'center';
    ctx.shadowColor = pop.color;
    ctx.shadowBlur = 8;
    ctx.fillText(pop.text, pop.x, pop.y);
    ctx.restore();
  }

  ctx.restore();
}

// Initial Starfield & Start render
initStars();
requestAnimationFrame(gameLoop);
