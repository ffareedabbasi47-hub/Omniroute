// game.js - Core Engine for My Talking Tom 2 Game
// Complete interactive cat physics, animation, rooms, voice mimic, and mini-game

(function() {
    'use strict';

    // Game State
    const state = {
        coins: parseInt(localStorage.getItem('tom_coins') || '150', 10),
        hunger: parseInt(localStorage.getItem('tom_hunger') || '75', 10),
        energy: parseInt(localStorage.getItem('tom_energy') || '85', 10),
        toilet: parseInt(localStorage.getItem('tom_toilet') || '40', 10), // 0 is empty, 100 is urgent
        hygiene: parseInt(localStorage.getItem('tom_hygiene') || '80', 10),
        happiness: parseInt(localStorage.getItem('tom_happiness') || '90', 10),
        currentRoom: 'living', // living, kitchen, bathroom, bedroom, doctor
        isSleeping: false,
        isSick: false,
        equipped: JSON.parse(localStorage.getItem('tom_equipped') || JSON.stringify({
            hat: 'none',
            glasses: 'none',
            fur: '#8e9aaf'
        })),
        unlockedItems: JSON.parse(localStorage.getItem('tom_unlocked') || JSON.stringify(['none', '#8e9aaf'])),
        soapBubbles: []
    };

    // Tom Character Procedural State
    const tom = {
        x: 210,
        y: 440,
        baseScale: 1.0,
        scaleY: 1.0,
        scaleX: 1.0,
        rotation: 0,
        headAngle: 0,
        // Eyes
        blinkTimer: 0,
        isBlinking: false,
        lookTargetX: 0,
        lookTargetY: 0,
        curLookX: 0,
        curLookY: 0,
        eyeState: 'normal', // normal, happy, dizzy, sleepy, surprised
        // Mouth
        mouthOpen: 0,
        mouthState: 'smile', // smile, open, talking, chew, ouch, fire
        // Action state
        action: 'idle', // idle, pet, slap, eat, sleep, toilet, sick, listening, talking
        actionTimer: 0,
        // Tail
        tailAngle: 0,
        tailPhase: 0,
        // Chewing counter
        chewPhase: 0,
        // Fire breath
        fireParticles: [],
        stars: [],
        hearts: []
    };

    // DOM Elements
    const canvas = document.getElementById('tom-canvas');
    const ctx = canvas.getContext('2d');
    const stage = document.getElementById('stage');
    const speechBubble = document.getElementById('speech-bubble');
    const coinCountEl = document.getElementById('coin-count');

    // Setup Canvas DPI
    function resizeCanvas() {
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        tom.x = rect.width / 2;
        tom.y = rect.height * 0.78;
    }

    // Save Game State
    function saveState() {
        localStorage.setItem('tom_coins', state.coins);
        localStorage.setItem('tom_hunger', state.hunger);
        localStorage.setItem('tom_energy', state.energy);
        localStorage.setItem('tom_toilet', state.toilet);
        localStorage.setItem('tom_hygiene', state.hygiene);
        localStorage.setItem('tom_happiness', state.happiness);
        localStorage.setItem('tom_equipped', JSON.stringify(state.equipped));
        localStorage.setItem('tom_unlocked', JSON.stringify(state.unlockedItems));
        updateMetersUI();
    }

    // Update UI Meters
    function updateMetersUI() {
        coinCountEl.textContent = state.coins;

        updateSingleMeter('meter-hunger', state.hunger, true);
        updateSingleMeter('meter-energy', state.energy, true);
        updateSingleMeter('meter-toilet', 100 - state.toilet, true); // Higher is cleaner
        updateSingleMeter('meter-hygiene', state.hygiene, true);
    }

    function updateSingleMeter(id, val, inverted = false) {
        const el = document.getElementById(id);
        if (!el) return;
        val = Math.max(0, Math.min(100, val));
        el.style.width = val + '%';
        el.className = 'meter-bar-inner ' + (val > 55 ? 'green' : val > 25 ? 'yellow' : 'red');
    }

    // Show Speech Bubble with Text
    let speechTimeout = null;
    function showSpeech(text, duration = 3000) {
        if (!speechBubble) return;
        speechBubble.textContent = text;
        speechBubble.classList.add('active');
        clearTimeout(speechTimeout);
        speechTimeout = setTimeout(() => {
            speechBubble.classList.remove('active');
        }, duration);
    }

    // Floating Particle Generator
    function createFloatingParticle(icon, x, y) {
        const p = document.createElement('div');
        p.className = 'particle';
        p.textContent = icon;
        p.style.left = (x || canvas.offsetLeft + canvas.offsetWidth / 2) + 'px';
        p.style.top = (y || canvas.offsetTop + 180) + 'px';
        stage.appendChild(p);
        setTimeout(() => p.remove(), 1200);
    }

    // --- PROCEDURAL DRAWING OF TOM THE CAT ---
    function drawTom() {
        const rect = canvas.getBoundingClientRect();
        ctx.clearRect(0, 0, rect.width, rect.height);

        ctx.save();
        ctx.translate(tom.x, tom.y);
        ctx.scale(tom.scaleX, tom.scaleY);
        ctx.rotate(tom.rotation);

        const fur = state.equipped.fur || '#8e9aaf';
        const belly = '#f8f9fa';
        const earInner = '#ffcad4';

        // 1. TAIL (Behind body)
        ctx.save();
        ctx.beginPath();
        ctx.strokeStyle = fur;
        ctx.lineWidth = 18;
        ctx.lineCap = 'round';
        const tailEnd = Math.sin(tom.tailPhase) * 28;
        ctx.moveTo(35, -20);
        ctx.quadraticCurveTo(80 + tailEnd, -70, 70 + tailEnd, -120);
        ctx.stroke();
        // Tail tip white fluff
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 12;
        ctx.beginPath();
        ctx.moveTo(70 + tailEnd, -105);
        ctx.lineTo(70 + tailEnd, -120);
        ctx.stroke();
        ctx.restore();

        // 2. FEET & PAWS (Bottom)
        // Left Foot
        ctx.fillStyle = fur;
        ctx.beginPath();
        ctx.ellipse(-55, 10, 26, 18, -0.1, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.ellipse(-55, 12, 18, 12, -0.1, 0, Math.PI * 2);
        ctx.fill();

        // Right Foot
        ctx.fillStyle = fur;
        ctx.beginPath();
        ctx.ellipse(55, 10, 26, 18, 0.1, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.ellipse(55, 12, 18, 12, 0.1, 0, Math.PI * 2);
        ctx.fill();

        // 3. BODY
        ctx.fillStyle = fur;
        ctx.beginPath();
        ctx.ellipse(0, -90, 75, 105, 0, 0, Math.PI * 2);
        ctx.fill();

        // White belly patch
        ctx.fillStyle = belly;
        ctx.beginPath();
        ctx.ellipse(0, -78, 48, 70, 0, 0, Math.PI * 2);
        ctx.fill();

        // Belly button cute dot
        ctx.fillStyle = '#ced4da';
        ctx.beginPath();
        ctx.arc(0, -50, 3, 0, Math.PI * 2);
        ctx.fill();

        // 4. ARMS & HANDS
        // Left Arm
        ctx.save();
        ctx.fillStyle = fur;
        ctx.beginPath();
        if (tom.action === 'listening') {
            // Raised to ear
            ctx.ellipse(-70, -140, 16, 45, 0.5, 0, Math.PI * 2);
        } else {
            ctx.ellipse(-72, -90, 18, 50, 0.25, 0, Math.PI * 2);
        }
        ctx.fill();
        // Hand
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(tom.action === 'listening' ? -65 : -76, tom.action === 'listening' ? -175 : -45, 15, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();

        // Right Arm
        ctx.save();
        ctx.fillStyle = fur;
        ctx.beginPath();
        ctx.ellipse(72, -90, 18, 50, -0.25, 0, Math.PI * 2);
        ctx.fill();
        // Hand
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(76, -45, 15, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();

        // Neck Bowtie or Bell
        if (state.equipped.neck === 'red_bowtie') {
            ctx.fillStyle = '#e63946';
            ctx.beginPath();
            ctx.moveTo(-16, -170); ctx.lineTo(-16, -150); ctx.lineTo(0, -160); ctx.fill();
            ctx.moveTo(16, -170); ctx.lineTo(16, -150); ctx.lineTo(0, -160); ctx.fill();
            ctx.fillStyle = '#ffbe0b';
            ctx.beginPath(); ctx.arc(0, -160, 6, 0, Math.PI * 2); ctx.fill();
        }

        // 5. HEAD
        ctx.save();
        ctx.translate(0, -195);
        ctx.rotate(tom.headAngle);

        // EARS
        // Left Ear
        ctx.fillStyle = fur;
        ctx.beginPath();
        ctx.moveTo(-75, -50);
        ctx.lineTo(-65, -125);
        ctx.lineTo(-20, -75);
        ctx.closePath();
        ctx.fill();
        // Inner Left Ear
        ctx.fillStyle = earInner;
        ctx.beginPath();
        ctx.moveTo(-68, -55);
        ctx.lineTo(-62, -112);
        ctx.lineTo(-26, -72);
        ctx.closePath();
        ctx.fill();

        // Right Ear
        ctx.fillStyle = fur;
        ctx.beginPath();
        ctx.moveTo(75, -50);
        ctx.lineTo(65, -125);
        ctx.lineTo(20, -75);
        ctx.closePath();
        ctx.fill();
        // Inner Right Ear
        ctx.fillStyle = earInner;
        ctx.beginPath();
        ctx.moveTo(68, -55);
        ctx.lineTo(62, -112);
        ctx.lineTo(26, -72);
        ctx.closePath();
        ctx.fill();

        // Big Round Cute Cat Face
        ctx.fillStyle = fur;
        ctx.beginPath();
        ctx.ellipse(0, -30, 88, 76, 0, 0, Math.PI * 2);
        ctx.fill();

        // White Muzzle / Cheeks
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.ellipse(-24, -14, 30, 22, -0.1, 0, Math.PI * 2);
        ctx.ellipse(24, -14, 30, 22, 0.1, 0, Math.PI * 2);
        ctx.fill();

        // Whiskers (3 on each side)
        ctx.strokeStyle = '#495057';
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        // Left whiskers
        ctx.moveTo(-45, -16); ctx.lineTo(-95, -26);
        ctx.moveTo(-45, -10); ctx.lineTo(-98, -10);
        ctx.moveTo(-45, -4);  ctx.lineTo(-92, 10);
        // Right whiskers
        ctx.moveTo(45, -16);  ctx.lineTo(95, -26);
        ctx.moveTo(45, -10);  ctx.lineTo(98, -10);
        ctx.moveTo(45, -4);   ctx.lineTo(92, 10);
        ctx.stroke();

        // Rosy Cheeks when Happy
        if (tom.eyeState === 'happy' || tom.action === 'pet') {
            ctx.fillStyle = 'rgba(255, 117, 143, 0.55)';
            ctx.beginPath();
            ctx.arc(-52, -15, 14, 0, Math.PI * 2);
            ctx.arc(52, -15, 14, 0, Math.PI * 2);
            ctx.fill();
        }

        // Cute Pink Heart Nose
        ctx.fillStyle = '#ff758f';
        ctx.beginPath();
        ctx.moveTo(0, -22);
        ctx.quadraticCurveTo(-10, -32, -12, -26);
        ctx.quadraticCurveTo(-10, -18, 0, -14);
        ctx.quadraticCurveTo(10, -18, 12, -26);
        ctx.quadraticCurveTo(10, -32, 0, -22);
        ctx.fill();

        // EYES
        drawEye(-36, -52, tom.curLookX, tom.curLookY, tom.eyeState, tom.isBlinking);
        drawEye(36, -52, tom.curLookX, tom.curLookY, tom.eyeState, tom.isBlinking);

        // MOUTH
        drawMouth();

        // SICK / BAND-AID (If in Doctor room or sick)
        if (state.isSick || state.currentRoom === 'doctor') {
            ctx.save();
            ctx.translate(-35, -80);
            ctx.rotate(-0.3);
            ctx.fillStyle = '#ffe3a8';
            ctx.fillRect(-18, -8, 36, 16);
            ctx.strokeStyle = '#e09f3e';
            ctx.strokeRect(-18, -8, 36, 16);
            ctx.fillStyle = '#e63946';
            ctx.fillRect(-3, -6, 6, 12);
            ctx.fillRect(-6, -3, 12, 6);
            ctx.restore();
        }

        // ACCESSORIES: GLASSES
        if (state.equipped.glasses === 'cool_shades') {
            ctx.fillStyle = '#111111';
            ctx.strokeStyle = '#333333';
            ctx.lineWidth = 3;
            // Left lens
            ctx.beginPath();
            ctx.roundRect(-66, -66, 56, 32, [8, 8, 18, 18]);
            ctx.fill(); ctx.stroke();
            // Right lens
            ctx.beginPath();
            ctx.roundRect(10, -66, 56, 32, [8, 8, 18, 18]);
            ctx.fill(); ctx.stroke();
            // Bridge
            ctx.beginPath();
            ctx.moveTo(-10, -56); ctx.lineTo(10, -56); ctx.stroke();
        }

        // ACCESSORIES: HATS
        drawHat(state.equipped.hat);

        ctx.restore(); // End head transform
        ctx.restore(); // End Tom transform

        // Render particles (Fire, Stars, Bubbles)
        renderEffects(rect);
    }

    // Helper: Draw Eye
    function drawEye(cx, cy, lookX, lookY, eyeState, blinking) {
        ctx.save();
        ctx.translate(cx, cy);

        if (blinking) {
            ctx.strokeStyle = '#343a40';
            ctx.lineWidth = 3.5;
            ctx.beginPath();
            ctx.arc(0, 0, 18, 0.2, Math.PI - 0.2);
            ctx.stroke();
            ctx.restore();
            return;
        }

        if (eyeState === 'happy') {
            ctx.strokeStyle = '#2b2d42';
            ctx.lineWidth = 4;
            ctx.beginPath();
            ctx.arc(0, 4, 16, Math.PI + 0.3, Math.PI * 2 - 0.3);
            ctx.stroke();
            ctx.restore();
            return;
        }

        if (eyeState === 'dizzy') {
            ctx.strokeStyle = '#7209b7';
            ctx.lineWidth = 3;
            ctx.beginPath();
            for (let a = 0; a < Math.PI * 4; a += 0.2) {
                const r = a * 3;
                const sx = Math.cos(a + Date.now() * 0.01) * r;
                const sy = Math.sin(a + Date.now() * 0.01) * r;
                if (a === 0) ctx.moveTo(sx, sy);
                else ctx.lineTo(sx, sy);
            }
            ctx.stroke();
            ctx.restore();
            return;
        }

        // Normal Eye Sclera (White)
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.ellipse(0, 0, 24, 28, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = '#dee2e6';
        ctx.lineWidth = 2;
        ctx.stroke();

        // Emerald Green Cartoon Iris
        const lx = lookX * 8;
        const ly = lookY * 8;
        ctx.fillStyle = '#2ec4b6';
        ctx.beginPath();
        ctx.arc(lx, ly, 15, 0, Math.PI * 2);
        ctx.fill();

        // Deep Pupil
        ctx.fillStyle = '#011627';
        ctx.beginPath();
        ctx.arc(lx, ly, 9, 0, Math.PI * 2);
        ctx.fill();

        // Glint / Reflections
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(lx - 4, ly - 4, 4.5, 0, Math.PI * 2);
        ctx.arc(lx + 3, ly + 3, 2.5, 0, Math.PI * 2);
        ctx.fill();

        ctx.restore();
    }

    // Helper: Draw Mouth
    function drawMouth() {
        ctx.save();
        ctx.translate(0, -6);

        if (tom.mouthState === 'open' || tom.mouthState === 'talking' || tom.mouthState === 'chew') {
            const h = Math.max(10, tom.mouthOpen * 26);
            ctx.fillStyle = '#67102a';
            ctx.beginPath();
            ctx.ellipse(0, h * 0.5, 18, h, 0, 0, Math.PI * 2);
            ctx.fill();

            // Pink Tongue
            ctx.fillStyle = '#ff758f';
            ctx.beginPath();
            ctx.ellipse(0, h * 0.8, 12, h * 0.5, 0, 0, Math.PI * 2);
            ctx.fill();

            // Teeth
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(-6, 0, 5, 5);
            ctx.fillRect(1, 0, 5, 5);
        } else {
            // Smiling cat mouth
            ctx.strokeStyle = '#343a40';
            ctx.lineWidth = 3;
            ctx.beginPath();
            ctx.moveTo(0, -8);
            ctx.lineTo(0, -2);
            ctx.stroke();

            ctx.beginPath();
            ctx.arc(-10, -2, 10, 0.1, Math.PI * 0.9);
            ctx.stroke();

            ctx.beginPath();
            ctx.arc(10, -2, 10, 0.1, Math.PI * 0.9);
            ctx.stroke();
        }

        ctx.restore();
    }

    // Helper: Draw Hats
    function drawHat(hat) {
        if (!hat || hat === 'none') return;
        ctx.save();

        if (hat === 'crown') {
            ctx.translate(0, -95);
            ctx.fillStyle = '#ffb703';
            ctx.beginPath();
            ctx.moveTo(-35, 10);
            ctx.lineTo(-40, -30);
            ctx.lineTo(-20, -10);
            ctx.lineTo(0, -40);
            ctx.lineTo(20, -10);
            ctx.lineTo(40, -30);
            ctx.lineTo(35, 10);
            ctx.closePath();
            ctx.fill();
            ctx.strokeStyle = '#fb8500';
            ctx.stroke();
            // Jewels
            ctx.fillStyle = '#d90429';
            ctx.beginPath(); ctx.arc(0, -20, 5, 0, Math.PI * 2); ctx.fill();
            ctx.fillStyle = '#4cc9f0';
            ctx.beginPath(); ctx.arc(-22, -10, 4, 0, Math.PI * 2); ctx.fill();
            ctx.beginPath(); ctx.arc(22, -10, 4, 0, Math.PI * 2); ctx.fill();
        } else if (hat === 'party') {
            ctx.translate(0, -100);
            ctx.fillStyle = '#f72585';
            ctx.beginPath();
            ctx.moveTo(-25, 15); ctx.lineTo(0, -65); ctx.lineTo(25, 15); ctx.closePath();
            ctx.fill();
            // Stripes
            ctx.strokeStyle = '#ffbe0b';
            ctx.lineWidth = 5;
            ctx.beginPath();
            ctx.moveTo(-12, -20); ctx.lineTo(12, -20);
            ctx.moveTo(-18, 0); ctx.lineTo(18, 0);
            ctx.stroke();
            // Yellow pom-pom
            ctx.fillStyle = '#ffbe0b';
            ctx.beginPath(); ctx.arc(0, -65, 8, 0, Math.PI * 2); ctx.fill();
        } else if (hat === 'nightcap') {
            ctx.translate(0, -95);
            ctx.fillStyle = '#3a0ca3';
            ctx.beginPath();
            ctx.moveTo(-35, 10);
            ctx.quadraticCurveTo(0, -70, 45, -50);
            ctx.quadraticCurveTo(20, -20, 35, 10);
            ctx.closePath();
            ctx.fill();
            ctx.fillStyle = '#ffbe0b';
            ctx.beginPath(); ctx.arc(45, -50, 9, 0, Math.PI * 2); ctx.fill();
        } else if (hat === 'cap') {
            ctx.translate(0, -85);
            ctx.fillStyle = '#4361ee';
            ctx.beginPath();
            ctx.arc(0, 0, 38, Math.PI, 0);
            ctx.fill();
            ctx.fillStyle = '#3a0ca3';
            ctx.beginPath();
            ctx.ellipse(30, 0, 32, 10, 0.2, 0, Math.PI * 2);
            ctx.fill();
        }

        ctx.restore();
    }

    // Render Particle Effects
    function renderEffects(rect) {
        // Fire particles
        for (let i = tom.fireParticles.length - 1; i >= 0; i--) {
            const p = tom.fireParticles[i];
            ctx.save();
            ctx.fillStyle = p.color;
            ctx.beginPath();
            ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
            ctx.fill();
            ctx.restore();

            p.x += p.vx;
            p.y += p.vy;
            p.size *= 0.95;
            p.life--;
            if (p.life <= 0 || p.size < 1) {
                tom.fireParticles.splice(i, 1);
            }
        }

        // Soap bubbles on Tom in shower
        state.soapBubbles.forEach(b => {
            ctx.save();
            ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
            ctx.strokeStyle = 'rgba(76, 201, 240, 0.8)';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.arc(tom.x + b.x, tom.y + b.y, b.size, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
            ctx.fillStyle = '#fff';
            ctx.beginPath();
            ctx.arc(tom.x + b.x - b.size * 0.3, tom.y + b.y - b.size * 0.3, b.size * 0.25, 0, Math.PI * 2);
            ctx.fill();
            ctx.restore();
        });
    }

    // --- MAIN GAME ANIMATION LOOP ---
    let lastTime = performance.now();
    function gameLoop(now) {
        const dt = (now - lastTime) / 1000;
        lastTime = now;

        // Tail sway
        tom.tailPhase += dt * 3;

        // Breathing oscillation
        const breath = Math.sin(now * 0.003) * 0.02;
        tom.scaleY = 1.0 + breath;
        tom.scaleX = 1.0 - breath * 0.5;

        // Eye Look interpolation towards target
        tom.curLookX += (tom.lookTargetX - tom.curLookX) * 0.1;
        tom.curLookY += (tom.lookTargetY - tom.curLookY) * 0.1;

        // Natural Random Blinking
        tom.blinkTimer += dt;
        if (!tom.isBlinking && tom.blinkTimer > 3.2 + Math.random() * 2) {
            tom.isBlinking = true;
            tom.blinkTimer = 0;
            setTimeout(() => { tom.isBlinking = false; }, 140);
        }

        // Talking mouth sync
        if (tom.action === 'talking') {
            tom.mouthOpen = 0.3 + Math.abs(Math.sin(now * 0.025)) * 0.7;
        }

        // Chewing mouth sync
        if (tom.action === 'eat') {
            tom.mouthOpen = Math.abs(Math.sin(now * 0.02)) * 0.8;
        }

        // Sleep state
        if (state.isSleeping) {
            tom.eyeState = 'sleepy';
            tom.headAngle = 0.1;
            if (Math.random() < 0.02) {
                createFloatingParticle('💤', canvas.offsetLeft + canvas.offsetWidth / 2 + 40, canvas.offsetTop + 160);
                if (window.tomAudio && Math.random() < 0.25) window.tomAudio.playSnore();
            }
        }

        drawTom();
        requestAnimationFrame(gameLoop);
    }

    // --- USER INTERACTIONS (TOUCH & POINTER) ---
    let isPetting = false;
    let petMoves = 0;
    let lastTouchX = 0;
    let lastTouchY = 0;

    canvas.addEventListener('pointerdown', (e) => {
        window.tomAudio && window.tomAudio.init();
        const rect = canvas.getBoundingClientRect();
        const px = e.clientX - rect.left;
        const py = e.clientY - rect.top;

        lastTouchX = px;
        lastTouchY = py;
        isPetting = true;
        petMoves = 0;

        // If sleeping, wake up
        if (state.isSleeping) {
            wakeTomUp();
            return;
        }

        // Hit Detection on Tom
        const dx = px - rect.width / 2;
        const dy = py - (rect.height * 0.78);

        // 1. Tapped Head (Face / Ears)
        if (dy < -130 && dy > -280 && Math.abs(dx) < 80) {
            handleTomPoke('head');
        }
        // 2. Tapped Feet / Tickle
        else if (dy > 0 && dy < 40 && Math.abs(dx) < 80) {
            handleTomTickle();
        }
    });

    canvas.addEventListener('pointermove', (e) => {
        const rect = canvas.getBoundingClientRect();
        const px = e.clientX - rect.left;
        const py = e.clientY - rect.top;

        // Follow user finger/cursor with eyes
        tom.lookTargetX = Math.max(-1, Math.min(1, (px - rect.width / 2) / 120));
        tom.lookTargetY = Math.max(-1, Math.min(1, (py - (rect.height * 0.5)) / 120));

        if (isPetting) {
            const dist = Math.hypot(px - lastTouchX, py - lastTouchY);
            if (dist > 8) {
                petMoves++;
                lastTouchX = px;
                lastTouchY = py;

                // When rubbing/petting
                if (petMoves > 4 && tom.action !== 'slap') {
                    triggerPettingReaction();
                }
            }
        }
    });

    window.addEventListener('pointerup', () => {
        if (isPetting) {
            isPetting = false;
            if (window.tomAudio) window.tomAudio.stopPurr();
            if (tom.action === 'pet') {
                tom.action = 'idle';
                tom.eyeState = 'normal';
                tom.mouthState = 'smile';
                tom.mouthOpen = 0;
            }
        }
    });

    // 1. Petting Reaction (Purr, smile, hearts)
    function triggerPettingReaction() {
        tom.action = 'pet';
        tom.eyeState = 'happy';
        tom.mouthState = 'smile';
        tom.headAngle = Math.sin(Date.now() * 0.005) * 0.08;

        if (window.tomAudio) window.tomAudio.startPurr();

        state.happiness = Math.min(100, state.happiness + 0.3);
        saveState();

        if (Math.random() < 0.15) {
            createFloatingParticle('💖', canvas.offsetLeft + tom.x + (Math.random() * 60 - 30), canvas.offsetTop + 180);
        }
    }

    // 2. Poke / Slap Reaction (Stumble, dizzy stars, squeak)
    function handleTomPoke(target) {
        if (tom.action === 'slap') return;
        tom.action = 'slap';
        tom.eyeState = 'dizzy';
        tom.mouthState = 'open';
        tom.mouthOpen = 0.8;
        tom.rotation = 0.15;
        tom.headAngle = -0.2;

        if (window.tomAudio) window.tomAudio.playSlap();

        createFloatingParticle('⭐', canvas.offsetLeft + tom.x - 30, canvas.offsetTop + 140);
        createFloatingParticle('✨', canvas.offsetLeft + tom.x + 30, canvas.offsetTop + 130);

        showSpeech('Arey baap re! 😵', 1500);

        setTimeout(() => {
            tom.rotation = -0.1;
        }, 120);

        setTimeout(() => {
            tom.rotation = 0;
            tom.headAngle = 0;
            tom.eyeState = 'normal';
            tom.mouthState = 'smile';
            tom.mouthOpen = 0;
            tom.action = 'idle';
        }, 1100);
    }

    // 3. Tickle Reaction (Giggle)
    function handleTomTickle() {
        tom.action = 'pet';
        tom.eyeState = 'happy';
        tom.mouthState = 'open';
        tom.mouthOpen = 0.6;
        tom.scaleY = 1.15;

        if (window.tomAudio) window.tomAudio.playGiggle();
        showSpeech('Hahaha gudgudi ho rahi hai! 😂', 1500);

        setTimeout(() => {
            tom.scaleY = 1.0;
            tom.eyeState = 'normal';
            tom.mouthState = 'smile';
            tom.mouthOpen = 0;
            tom.action = 'idle';
        }, 1200);
    }

    // --- ROOM SWITCHING SYSTEM ---
    const navTabs = document.querySelectorAll('.nav-tab');
    navTabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const room = tab.dataset.room;
            switchRoom(room);
        });
    });

    function switchRoom(room) {
        state.currentRoom = room;
        navTabs.forEach(t => t.classList.toggle('active', t.dataset.room === room));

        // Switch Stage background theme
        stage.className = 'room-' + room;

        // Toggle drawers/trays
        document.getElementById('kitchen-tray').classList.toggle('hidden', room !== 'kitchen');
        document.getElementById('bathroom-tray').classList.toggle('hidden', room !== 'bathroom');
        document.getElementById('doctor-tray').classList.toggle('hidden', room !== 'doctor');
        document.getElementById('talk-bar').classList.toggle('hidden', room !== 'living');
        document.getElementById('quick-phrases').classList.toggle('hidden', room !== 'living');
        document.getElementById('pet-companion').style.display = room === 'living' ? 'block' : 'none';

        // Bedroom lamp
        const lamp = document.getElementById('lamp-toggle');
        if (lamp) lamp.style.display = room === 'bedroom' ? 'block' : 'none';

        // Shower controls in bathroom
        const showerHead = document.getElementById('shower-head');
        if (showerHead) showerHead.style.display = room === 'bathroom' ? 'block' : 'none';

        if (window.tomAudio) window.tomAudio.playMeow(1.2);
    }

    // --- KITCHEN: FOOD & FEEDING MECHANICS ---
    const foodItems = document.querySelectorAll('.food-item');
    foodItems.forEach(item => {
        item.addEventListener('click', () => {
            feedTom(item.dataset.food);
        });
    });

    function feedTom(foodType) {
        if (tom.action === 'eat') return;
        tom.action = 'eat';
        tom.eyeState = 'happy';
        tom.mouthState = 'open';

        if (window.tomAudio) {
            window.tomAudio.playMunch();
            setTimeout(() => window.tomAudio.playMunch(), 200);
            setTimeout(() => window.tomAudio.playMunch(), 400);
            setTimeout(() => window.tomAudio.playGulpAndBurp(), 650);
        }

        // Special Chili Event! 🔥🌶️
        if (foodType === 'chili') {
            setTimeout(() => {
                triggerChiliReaction();
            }, 750);
            return;
        }

        // Normal Food
        state.hunger = Math.min(100, state.hunger + 25);
        state.coins += 2;
        saveState();

        createFloatingParticle('😋', canvas.offsetLeft + tom.x, canvas.offsetTop + 180);
        showSpeech('Maza aa gaya! Yammi! 🤤', 2000);

        setTimeout(() => {
            tom.action = 'idle';
            tom.eyeState = 'normal';
            tom.mouthState = 'smile';
            tom.mouthOpen = 0;
        }, 1200);
    }

    function triggerChiliReaction() {
        tom.eyeState = 'dizzy';
        tom.mouthState = 'open';
        tom.mouthOpen = 1.0;
        showSpeech('AAG LAG GAYI! MIRCHI!! 🌶️🔥', 2500);

        if (window.tomAudio) window.tomAudio.playFireBreath();

        for (let i = 0; i < 40; i++) {
            tom.fireParticles.push({
                x: tom.x,
                y: tom.y - 190,
                vx: (Math.random() - 0.5) * 8,
                vy: -Math.random() * 12 - 2,
                size: 15 + Math.random() * 20,
                color: Math.random() < 0.5 ? '#ff4d6d' : '#ffbe0b',
                life: 35
            });
        }

        setTimeout(() => {
            tom.action = 'idle';
            tom.eyeState = 'normal';
            tom.mouthState = 'smile';
            tom.mouthOpen = 0;
        }, 2200);
    }

    // --- BATHROOM: TOILET & SHOWER ---
    const toiletBtn = document.getElementById('btn-toilet');
    if (toiletBtn) {
        toiletBtn.addEventListener('click', () => {
            tom.action = 'toilet';
            tom.eyeState = 'dizzy';
            showSpeech('Pooo... Fart! 💨🚽', 2000);

            if (window.tomAudio) {
                window.tomAudio.playFart();
                setTimeout(() => window.tomAudio.playFlush(), 450);
            }

            state.toilet = 0;
            state.coins += 5;
            saveState();

            createFloatingParticle('💨', canvas.offsetLeft + tom.x, canvas.offsetTop + 280);

            setTimeout(() => {
                tom.action = 'idle';
                tom.eyeState = 'happy';
                showSpeech('Aaha! Kitna halka feel ho raha hai! 😌', 2000);
                setTimeout(() => {
                    tom.eyeState = 'normal';
                }, 1500);
            }, 1800);
        });
    }

    // Shower Soap & Water
    const soapBtn = document.getElementById('btn-soap');
    if (soapBtn) {
        soapBtn.addEventListener('click', () => {
            for (let i = 0; i < 8; i++) {
                state.soapBubbles.push({
                    x: (Math.random() - 0.5) * 120,
                    y: -80 - Math.random() * 140,
                    size: 8 + Math.random() * 14
                });
            }
            if (window.tomAudio) window.tomAudio.playBubblePop();
            createFloatingParticle('🫧', canvas.offsetLeft + tom.x, canvas.offsetTop + 220);
        });
    }

    const showerHead = document.getElementById('shower-head');
    const showerWater = document.getElementById('shower-water');
    if (showerHead) {
        showerHead.addEventListener('click', () => {
            showerWater.classList.add('spraying');
            if (window.tomAudio) window.tomAudio.playWaterSpray();

            setTimeout(() => {
                state.soapBubbles = [];
                showerWater.classList.remove('spraying');
                state.hygiene = 100;
                state.coins += 5;
                saveState();

                createFloatingParticle('✨', canvas.offsetLeft + tom.x, canvas.offsetTop + 180);
                if (window.tomAudio) window.tomAudio.playCureSparkle();
                showSpeech('Tom ekdum saaf aur chamakdar ho gaya! 🧼', 2200);
            }, 1200);
        });
    }

    // --- BEDROOM: SLEEP & LAMP ---
    const lampToggle = document.getElementById('lamp-toggle');
    if (lampToggle) {
        lampToggle.addEventListener('click', () => {
            if (state.isSleeping) {
                wakeTomUp();
            } else {
                putTomToSleep();
            }
        });
    }

    function putTomToSleep() {
        state.isSleeping = true;
        stage.classList.add('dark-mode');
        lampToggle.textContent = '🌙';
        tom.action = 'sleep';
        state.equipped.hat = 'nightcap';
        showSpeech('Shhh... Tom so raha hai! Good night! 😴', 2000);
        if (window.tomAudio) window.tomAudio.playSnore();
    }

    function wakeTomUp() {
        state.isSleeping = false;
        stage.classList.remove('dark-mode');
        lampToggle.textContent = '💡';
        tom.action = 'idle';
        tom.eyeState = 'normal';
        state.equipped.hat = 'none';
        state.energy = 100;
        state.coins += 10;
        saveState();

        if (window.tomAudio) window.tomAudio.playMeow(1.3);
        showSpeech('Suprabhat! Full energy aa gayi! ⚡', 2000);
    }

    // --- DOCTOR CLINIC (MY TALKING TOM 2 FIRST AID) ---
    const doctorTools = document.querySelectorAll('.tool-item');
    doctorTools.forEach(tool => {
        tool.addEventListener('click', () => {
            const t = tool.dataset.tool;
            handleDoctorTool(t);
        });
    });

    function handleDoctorTool(tool) {
        if (tool === 'syringe') {
            tom.eyeState = 'dizzy';
            tom.scaleY = 1.2;
            if (window.tomAudio) window.tomAudio.playSlap();
            showSpeech('Ouch! Injection laga diya! 💉😭', 1800);
            setTimeout(() => {
                tom.scaleY = 1.0;
                cureTom();
            }, 1000);
        } else if (tool === 'potion') {
            if (window.tomAudio) window.tomAudio.playGulpAndBurp();
            cureTom();
        } else if (tool === 'bandage') {
            cureTom();
        }
    }

    function cureTom() {
        state.isSick = false;
        state.happiness = 100;
        state.coins += 15;
        saveState();

        if (window.tomAudio) window.tomAudio.playCureSparkle();
        createFloatingParticle('💖', canvas.offsetLeft + tom.x, canvas.offsetTop + 160);
        createFloatingParticle('✨', canvas.offsetLeft + tom.x + 30, canvas.offsetTop + 180);

        showSpeech('Wah! Tom bilkul theek aur tandurust ho gaya! 🩺✨', 2500);
    }

    // --- VOICE MIMIC & TALKING ENGINE ---
    const micBtn = document.getElementById('mic-btn');
    const talkInput = document.getElementById('talk-input');
    const sendSpeechBtn = document.getElementById('send-speech-btn');

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    let recognition = null;
    if (SpeechRecognition) {
        recognition = new SpeechRecognition();
        recognition.continuous = false;
        recognition.interimResults = false;
        recognition.lang = 'hi-IN';

        recognition.onstart = () => {
            micBtn.classList.add('listening');
            tom.action = 'listening';
            showSpeech('Sun raha hoon... bolo! 👂', 3000);
        };

        recognition.onresult = (event) => {
            const transcript = event.results[0][0].transcript;
            micBtn.classList.remove('listening');
            talkInput.value = transcript;
            tomSpeak(transcript);
        };

        recognition.onerror = () => {
            micBtn.classList.remove('listening');
            tom.action = 'idle';
            showSpeech('Aawaz nahi suni, dubara kaho ya likho!', 2000);
        };

        recognition.onend = () => {
            micBtn.classList.remove('listening');
            if (tom.action === 'listening') tom.action = 'idle';
        };
    }

    if (micBtn) {
        micBtn.addEventListener('click', () => {
            if (recognition) {
                try {
                    recognition.start();
                } catch(e) {
                    recognition.stop();
                }
            } else {
                showSpeech('Mic enable nahi hai, niche likh kar Tom ko bolo! ✍️', 2500);
            }
        });
    }

    function tomSpeak(text) {
        if (!text) return;
        tom.action = 'talking';
        tom.eyeState = 'happy';
        showSpeech(text, 3500);

        if (window.tomAudio) {
            window.tomAudio.speakTomVoice(text, 
                () => { tom.action = 'talking'; }, 
                () => { 
                    tom.action = 'idle';
                    tom.eyeState = 'normal';
                    tom.mouthState = 'smile';
                    tom.mouthOpen = 0;
                }
            );
        }
    }

    if (sendSpeechBtn) {
        sendSpeechBtn.addEventListener('click', () => {
            const txt = talkInput.value.trim();
            if (txt) {
                tomSpeak(txt);
                talkInput.value = '';
            }
        });
    }

    if (talkInput) {
        talkInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const txt = talkInput.value.trim();
                if (txt) {
                    tomSpeak(txt);
                    talkInput.value = '';
                }
            }
        });
    }

    // Quick Phrases
    const quickPhrases = document.querySelectorAll('.phrase-pill');
    quickPhrases.forEach(p => {
        p.addEventListener('click', () => {
            tomSpeak(p.dataset.phrase);
        });
    });

    // Pet Companion (Squeezer)
    const petCompanion = document.getElementById('pet-companion');
    if (petCompanion) {
        petCompanion.addEventListener('click', () => {
            if (window.tomAudio) window.tomAudio.playGiggle();
            createFloatingParticle('⭐', petCompanion.offsetLeft + 20, petCompanion.offsetTop - 20);
            showSpeech('Tom ka pyara dost Squeezer! 🐹💖', 1800);
        });
    }

    // --- WARDROBE & COSTUME SHOP MODAL ---
    const shopModal = document.getElementById('shop-modal');
    const openShopBtn = document.getElementById('btn-open-shop');
    const closeShopBtn = document.getElementById('btn-close-shop');
    const shopGrid = document.getElementById('shop-grid');

    const shopCatalog = [
        { id: 'hat_crown', type: 'hat', val: 'crown', name: 'Shahi Crown 👑', price: 50 },
        { id: 'hat_party', type: 'hat', val: 'party', name: 'Party Hat 🥳', price: 30 },
        { id: 'hat_cap', type: 'hat', val: 'cap', name: 'Cool Cap 🧢', price: 40 },
        { id: 'shades', type: 'glasses', val: 'cool_shades', name: 'Kala Chashma 🕶️', price: 60 },
        { id: 'bowtie', type: 'neck', val: 'red_bowtie', name: 'Red Bowtie 🎀', price: 35 },
        { id: 'fur_ginger', type: 'fur', val: '#f4a261', name: 'Ginger Fur 🐱', price: 80 },
        { id: 'fur_black', type: 'fur', val: '#2b2d42', name: 'Black Panther 🐾', price: 90 },
        { id: 'fur_white', type: 'fur', val: '#e9ecef', name: 'White Snow ❄️', price: 80 },
        { id: 'reset_style', type: 'reset', val: 'reset', name: 'Default Look 🔄', price: 0 }
    ];

    function renderShop() {
        if (!shopGrid) return;
        shopGrid.innerHTML = '';
        shopCatalog.forEach(item => {
            const isOwned = state.unlockedItems.includes(item.val) || item.price === 0;
            const isEquipped = state.equipped[item.type] === item.val;

            const card = document.createElement('div');
            card.className = 'shop-item-card' + (isEquipped ? ' equipped' : '');
            card.innerHTML = `
                <div class="shop-item-icon">${item.name.split(' ').pop()}</div>
                <div class="shop-item-name">${item.name}</div>
                <button class="shop-item-btn ${isEquipped ? 'active' : isOwned ? 'owned' : ''}">
                    ${isEquipped ? 'Equipped' : isOwned ? 'Wear' : '🪙 ' + item.price}
                </button>
            `;

            card.addEventListener('click', () => {
                if (item.type === 'reset') {
                    state.equipped.hat = 'none';
                    state.equipped.glasses = 'none';
                    state.equipped.neck = 'none';
                    state.equipped.fur = '#8e9aaf';
                    saveState();
                    renderShop();
                    return;
                }

                if (isOwned) {
                    state.equipped[item.type] = (state.equipped[item.type] === item.val) ? 'none' : item.val;
                } else if (state.coins >= item.price) {
                    state.coins -= item.price;
                    state.unlockedItems.push(item.val);
                    state.equipped[item.type] = item.val;
                    if (window.tomAudio) window.tomAudio.playCoin();
                } else {
                    showSpeech('Coins kam hain! Mini-game khelo! 🪙', 2000);
                    return;
                }
                saveState();
                renderShop();
            });

            shopGrid.appendChild(card);
        });
    }

    if (openShopBtn) openShopBtn.addEventListener('click', () => { renderShop(); shopModal.classList.add('open'); });
    if (closeShopBtn) closeShopBtn.addEventListener('click', () => shopModal.classList.remove('open'));

    // --- ARCADE MINI-GAME: "TOM'S SNACK CATCHER" ---
    const minigameModal = document.getElementById('minigame-modal');
    const openGameBtn = document.getElementById('btn-open-game');
    const closeGameBtn = document.getElementById('btn-close-game');
    const mgCanvas = document.getElementById('minigame-canvas');
    let mgCtx = null;
    let mgActive = false;
    let mgScore = 0;
    let mgBasketX = 140;
    let mgSnacks = [];
    let mgAnimationId = null;

    if (mgCanvas) {
        mgCtx = mgCanvas.getContext('2d');
    }

    function startMiniGame() {
        mgActive = true;
        mgScore = 0;
        mgSnacks = [];
        mgBasketX = mgCanvas.width / 2;
        minigameModal.classList.add('open');
        lastSpawnTime = performance.now();
        miniGameLoop();
    }

    function stopMiniGame() {
        mgActive = false;
        cancelAnimationFrame(mgAnimationId);
        minigameModal.classList.remove('open');
    }

    let lastSpawnTime = 0;
    function miniGameLoop() {
        if (!mgActive) return;
        const now = performance.now();

        mgCtx.clearRect(0, 0, mgCanvas.width, mgCanvas.height);

        if (now - lastSpawnTime > 750) {
            lastSpawnTime = now;
            const isBomb = Math.random() < 0.2;
            mgSnacks.push({
                x: 20 + Math.random() * (mgCanvas.width - 40),
                y: -20,
                speed: 2.5 + Math.random() * 2,
                type: isBomb ? '💣' : ['🐟', '🍔', '🍼', '🍰'][Math.floor(Math.random() * 4)],
                isBomb: isBomb
            });
        }

        for (let i = mgSnacks.length - 1; i >= 0; i--) {
            const s = mgSnacks[i];
            s.y += s.speed;

            mgCtx.font = '28px Arial';
            mgCtx.fillText(s.type, s.x, s.y);

            if (s.y > mgCanvas.height - 45 && s.y < mgCanvas.height - 10 && Math.abs(s.x - mgBasketX) < 35) {
                if (s.isBomb) {
                    if (window.tomAudio) window.tomAudio.playSlap();
                    alert('Boom! Bomb phoot gaya! Final Score: ' + mgScore);
                    state.coins += Math.floor(mgScore / 2);
                    saveState();
                    stopMiniGame();
                    return;
                } else {
                    mgScore += 10;
                    if (window.tomAudio) window.tomAudio.playCoin();
                    mgSnacks.splice(i, 1);
                    continue;
                }
            }

            if (s.y > mgCanvas.height) {
                mgSnacks.splice(i, 1);
            }
        }

        // Draw Player Catcher Bowl
        mgCtx.fillStyle = '#ffb703';
        mgCtx.beginPath();
        mgCtx.ellipse(mgBasketX, mgCanvas.height - 25, 35, 14, 0, 0, Math.PI);
        mgCtx.fill();
        mgCtx.fillStyle = '#e63946';
        mgCtx.font = '20px Arial';
        mgCtx.fillText('🐱', mgBasketX - 10, mgCanvas.height - 18);

        // Draw Score UI
        mgCtx.fillStyle = '#ffffff';
        mgCtx.font = 'bold 18px Nunito, sans-serif';
        mgCtx.fillText('Score: ' + mgScore, 14, 30);

        mgAnimationId = requestAnimationFrame(miniGameLoop);
    }

    if (openGameBtn) openGameBtn.addEventListener('click', startMiniGame);
    if (closeGameBtn) closeGameBtn.addEventListener('click', stopMiniGame);

    const btnLeft = document.getElementById('mg-btn-left');
    const btnRight = document.getElementById('mg-btn-right');
    if (btnLeft) {
        btnLeft.addEventListener('pointerdown', () => { mgBasketX = Math.max(35, mgBasketX - 45); });
    }
    if (btnRight) {
        btnRight.addEventListener('pointerdown', () => { mgBasketX = Math.min(mgCanvas.width - 35, mgBasketX + 45); });
    }

    if (mgCanvas) {
        mgCanvas.addEventListener('pointermove', (e) => {
            const rect = mgCanvas.getBoundingClientRect();
            mgBasketX = Math.max(35, Math.min(mgCanvas.width - 35, e.clientX - rect.left));
        });
    }

    // Fullscreen Toggle
    const fullscreenBtn = document.getElementById('btn-fullscreen');
    if (fullscreenBtn) {
        fullscreenBtn.addEventListener('click', () => {
            if (!document.fullscreenElement) {
                document.documentElement.requestFullscreen().catch(() => {});
            } else {
                document.exitFullscreen().catch(() => {});
            }
        });
    }

    // Sound Toggle
    const soundBtn = document.getElementById('btn-sound');
    if (soundBtn) {
        soundBtn.addEventListener('click', () => {
            if (window.tomAudio) {
                const muted = window.tomAudio.toggleMute();
                soundBtn.textContent = muted ? '🔇' : '🔊';
            }
        });
    }

    // Natural Need Degradation Over Time
    setInterval(() => {
        if (!state.isSleeping) {
            state.hunger = Math.max(0, state.hunger - 1);
            state.energy = Math.max(0, state.energy - 0.5);
            state.toilet = Math.min(100, state.toilet + 1);
            state.hygiene = Math.max(0, state.hygiene - 0.5);
            saveState();
        }
    }, 15000);

    // Initial Setup
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();
    updateMetersUI();
    switchRoom('living');
    requestAnimationFrame(gameLoop);

    // Welcome greeting
    setTimeout(() => {
        showSpeech('Namaste! Main hoon Tom! Mere sath khelo! 😸💖', 3000);
        if (window.tomAudio) window.tomAudio.playMeow(1.2);
    }, 600);

})();
