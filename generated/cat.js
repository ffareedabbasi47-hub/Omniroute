// ==========================================================================
// Talking Tom - Complete 2D Character Canvas Renderer & Physics Engine
// ==========================================================================

class TomCat {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');

    this.state = 'idle';
    this.stateTimer = 0;

    this.mouthOpen = 0;
    this.targetMouthOpen = 0;
    this.blinkProgress = 0;
    this.blinkTimer = 180;
    this.earTwitchTimer = 120;
    this.earTwitchAngle = 0;

    this.lookX = 0;
    this.lookY = 0;
    this.targetLookX = 0;
    this.targetLookY = 0;

    this.breathTime = 0;
    this.tailAngle = 0;
    this.bodyWobble = 0;
    this.headWobble = 0;

    this.accessory = 'none';
    this.isSoaped = false;
    this.soapBubbles = [];

    this.hitboxes = {
      head: { x: 0, y: 0, r: 0 },
      belly: { x: 0, y: 0, r: 0 },
      leftFoot: { x: 0, y: 0, r: 0 },
      rightFoot: { x: 0, y: 0, r: 0 }
    };

    this.resize();
    window.addEventListener('resize', () => this.resize());
  }

  resize() {
    const rect = this.canvas.parentElement.getBoundingClientRect();
    this.width = rect.width;
    this.height = rect.height;

    const dpr = window.devicePixelRatio || 1;
    this.canvas.width = this.width * dpr;
    this.canvas.height = this.height * dpr;
    this.ctx.resetTransform();
    this.ctx.scale(dpr, dpr);
  }

  setMouthOpen(val) {
    this.targetMouthOpen = Math.min(Math.max(val, 0), 1);
  }

  setLookTarget(canvasX, canvasY) {
    const cx = this.width / 2;
    const cy = this.height * 0.42;
    const dx = canvasX - cx;
    const dy = canvasY - cy;
    this.targetLookX = Math.min(Math.max(dx / 180, -1), 1);
    this.targetLookY = Math.min(Math.max(dy / 180, -1), 1);
  }

  triggerSlap() {
    this.state = 'slapped';
    this.stateTimer = 45;
    this.headWobble = 28;
  }

  triggerGiggle() {
    this.state = 'giggle';
    this.stateTimer = 50;
  }

  triggerSpicy() {
    this.state = 'spicy';
    this.stateTimer = 90;
  }

  triggerEat() {
    this.state = 'eating';
    this.stateTimer = 50;
  }

  update() {
    this.breathTime += 0.05;
    this.tailAngle = Math.sin(this.breathTime * 0.8) * 0.35;
    this.mouthOpen += (this.targetMouthOpen - this.mouthOpen) * 0.35;

    this.lookX += (this.targetLookX - this.lookX) * 0.12;
    this.lookY += (this.targetLookY - this.lookY) * 0.12;

    this.blinkTimer--;
    if (this.blinkTimer <= 0) {
      this.blinkProgress += 0.25;
      if (this.blinkProgress >= 1) {
        this.blinkProgress = 0;
        this.blinkTimer = 160 + Math.random() * 140;
      }
    }

    this.earTwitchTimer--;
    if (this.earTwitchTimer <= 0) {
      this.earTwitchAngle = Math.sin(this.earTwitchTimer * 0.6) * 0.15;
      if (this.earTwitchTimer < -15) {
        this.earTwitchTimer = 100 + Math.random() * 120;
        this.earTwitchAngle = 0;
      }
    }

    this.headWobble *= 0.88;
    this.bodyWobble *= 0.88;

    if (this.stateTimer > 0) {
      this.stateTimer--;
      if (this.state === 'eating') {
        this.mouthOpen = Math.abs(Math.sin(this.stateTimer * 0.45)) * 0.75;
      }
      if (this.state === 'giggle') {
        this.bodyWobble = Math.sin(this.stateTimer * 0.8) * 8;
      }
      if (this.stateTimer <= 0 && this.state !== 'sleeping') {
        this.state = 'idle';
      }
    }

    if (this.isSoaped && this.soapBubbles.length < 24 && Math.random() < 0.2) {
      this.soapBubbles.push({
        x: (Math.random() - 0.5) * 160,
        y: (Math.random() - 0.5) * 220,
        r: 5 + Math.random() * 14
      });
    }
  }

  render() {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.width, this.height);

    const cx = this.width / 2;
    const breathOffset = Math.sin(this.breathTime) * 3;
    const cy = this.height * 0.60 + breathOffset;

    this.hitboxes.head = { x: cx, y: cy - 130, r: 75 };
    this.hitboxes.belly = { x: cx, y: cy - 10, r: 65 };
    this.hitboxes.leftFoot = { x: cx - 55, y: cy + 90, r: 35 };
    this.hitboxes.rightFoot = { x: cx + 55, y: cy + 90, r: 35 };

    ctx.save();
    ctx.translate(cx + Math.sin(this.headWobble) * 4, cy);

    this.drawShadow(ctx, 0, 105, 110 - breathOffset * 2, 26);
    this.drawTail(ctx, -50, 45);
    this.drawBody(ctx, 0, 0);
    this.drawFeet(ctx, 0, 85);

    ctx.save();
    ctx.translate(0, -115 + (this.state === 'slapped' ? Math.sin(this.headWobble * 2) * 15 : 0));
    ctx.rotate((this.headWobble * Math.PI) / 180);

    this.drawEars(ctx, 0, -25);
    this.drawHead(ctx, 0, 0);
    this.drawFace(ctx, 0, 0);
    this.drawAccessories(ctx, 0, 0);

    ctx.restore();

    this.drawPaws(ctx, 0, -10);

    if (this.accessory === 'bowtie') {
      this.drawBowtie(ctx, 0, -55);
    }
    if (this.isSoaped) {
      this.drawSoapBubbles(ctx);
    }
    if (this.state === 'spicy' && this.stateTimer > 0) {
      this.drawFireBreath(ctx, 0, -85);
    }
    if (this.state === 'sleeping') {
      this.drawZzz(ctx, 40, -160);
    }

    ctx.restore();
  }

  drawShadow(ctx, x, y, rx, ry) {
    ctx.beginPath();
    ctx.ellipse(x, y, Math.max(rx, 10), Math.max(ry, 5), 0, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0, 0, 0, 0.28)';
    ctx.fill();
  }

  drawTail(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(this.tailAngle);
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.bezierCurveTo(-50, -10, -70, -70, -45, -100);
    ctx.bezierCurveTo(-35, -110, -25, -100, -32, -90);
    ctx.bezierCurveTo(-45, -60, -20, -15, 10, 5);
    ctx.closePath();
    ctx.fillStyle = '#6b7280';
    ctx.fill();
    ctx.strokeStyle = '#4b5563';
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.beginPath();
    ctx.arc(-42, -98, 12, 0, Math.PI * 2);
    ctx.fillStyle = '#f3f4f6';
    ctx.fill();
    ctx.restore();
  }

  drawBody(ctx, x, y) {
    ctx.save();
    ctx.translate(x + this.bodyWobble, y);
    ctx.beginPath();
    ctx.ellipse(0, 5, 78, 92, 0, 0, Math.PI * 2);
    const grad = ctx.createRadialGradient(-15, -10, 10, 0, 10, 95);
    grad.addColorStop(0, '#8e96a8');
    grad.addColorStop(1, '#565c6e');
    ctx.fillStyle = grad;
    ctx.fill();
    ctx.strokeStyle = '#3e4352';
    ctx.lineWidth = 3.5;
    ctx.stroke();

    ctx.beginPath();
    ctx.ellipse(0, 14, 52, 62, 0, 0, Math.PI * 2);
    const bellyGrad = ctx.createLinearGradient(0, -40, 0, 70);
    bellyGrad.addColorStop(0, '#ffffff');
    bellyGrad.addColorStop(1, '#e2e6f0');
    ctx.fillStyle = bellyGrad;
    ctx.fill();
    ctx.restore();
  }

  drawFeet(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);
    const drawFoot = (fx, isLeft) => {
      ctx.save();
      ctx.translate(fx, 0);
      ctx.beginPath();
      ctx.ellipse(0, 0, 32, 20, isLeft ? -0.1 : 0.1, 0, Math.PI * 2);
      ctx.fillStyle = '#7a8194';
      ctx.fill();
      ctx.strokeStyle = '#3e4352';
      ctx.lineWidth = 3;
      ctx.stroke();

      ctx.fillStyle = '#ff9eb5';
      ctx.beginPath();
      ctx.ellipse(0, 2, 14, 9, 0, 0, Math.PI * 2);
      ctx.fill();

      [-15, 0, 15].forEach(bx => {
        ctx.beginPath();
        ctx.arc(bx, -10, 4.5, 0, Math.PI * 2);
        ctx.fill();
      });
      ctx.restore();
    };

    drawFoot(-55, true);
    drawFoot(55, false);
    ctx.restore();
  }

  drawEars(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);

    // Left
    ctx.save();
    ctx.translate(-50, -45);
    ctx.rotate(-0.25 + this.earTwitchAngle);
    ctx.beginPath();
    ctx.moveTo(-25, 30);
    ctx.quadraticCurveTo(-40, -45, -10, -55);
    ctx.quadraticCurveTo(25, -45, 20, 25);
    ctx.closePath();
    ctx.fillStyle = '#7a8194';
    ctx.fill();
    ctx.strokeStyle = '#3e4352';
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(-15, 20);
    ctx.quadraticCurveTo(-26, -30, -8, -40);
    ctx.quadraticCurveTo(12, -30, 10, 15);
    ctx.closePath();
    ctx.fillStyle = '#fca5a5';
    ctx.fill();
    ctx.restore();

    // Right
    ctx.save();
    ctx.translate(50, -45);
    ctx.rotate(0.25 - this.earTwitchAngle);
    ctx.beginPath();
    ctx.moveTo(25, 30);
    ctx.quadraticCurveTo(40, -45, 10, -55);
    ctx.quadraticCurveTo(-25, -45, -20, 25);
    ctx.closePath();
    ctx.fillStyle = '#7a8194';
    ctx.fill();
    ctx.strokeStyle = '#3e4352';
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(15, 20);
    ctx.quadraticCurveTo(26, -30, 8, -40);
    ctx.quadraticCurveTo(-12, -30, -10, 15);
    ctx.closePath();
    ctx.fillStyle = '#fca5a5';
    ctx.fill();
    ctx.restore();

    ctx.restore();
  }

  drawHead(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);

    ctx.beginPath();
    ctx.moveTo(-75, -20);
    ctx.bezierCurveTo(-98, 20, -85, 65, -30, 72);
    ctx.bezierCurveTo(-10, 75, 10, 75, 30, 72);
    ctx.bezierCurveTo(85, 65, 98, 20, 75, -20);
    ctx.bezierCurveTo(65, -70, -65, -70, -75, -20);
    ctx.closePath();

    const headGrad = ctx.createRadialGradient(-10, -10, 20, 0, 0, 95);
    headGrad.addColorStop(0, '#959eaf');
    headGrad.addColorStop(1, '#5d6475');
    ctx.fillStyle = headGrad;
    ctx.fill();
    ctx.strokeStyle = '#3e4352';
    ctx.lineWidth = 3.5;
    ctx.stroke();

    ctx.beginPath();
    ctx.ellipse(-24, 34, 30, 22, -0.15, 0, Math.PI * 2);
    ctx.ellipse(24, 34, 30, 22, 0.15, 0, Math.PI * 2);
    ctx.fillStyle = '#f8fafc';
    ctx.fill();

    ctx.fillStyle = 'rgba(255, 107, 129, 0.35)';
    ctx.beginPath();
    ctx.ellipse(-52, 28, 14, 9, -0.1, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.ellipse(52, 28, 14, 9, 0.1, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  drawFace(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);

    const drawEye = (ex) => {
      ctx.save();
      ctx.translate(ex, -5);

      if (this.state === 'petting') {
        ctx.beginPath();
        ctx.arc(0, 0, 18, Math.PI * 0.95, Math.PI * 2.05, false);
        ctx.strokeStyle = '#2d3342';
        ctx.lineWidth = 5;
        ctx.lineCap = 'round';
        ctx.stroke();
        ctx.restore();
        return;
      }

      if (this.state === 'slapped') {
        ctx.beginPath();
        ctx.arc(0, 0, 16, 0, Math.PI * 2);
        ctx.fillStyle = '#fff';
        ctx.fill();
        ctx.strokeStyle = '#3e4352';
        ctx.lineWidth = 3;
        ctx.stroke();

        ctx.beginPath();
        for (let a = 0; a < Math.PI * 4; a += 0.2) {
          const r = a * 2.6;
          const px = Math.cos(a + this.headWobble) * r;
          const py = Math.sin(a + this.headWobble) * r;
          if (a === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
        }
        ctx.strokeStyle = '#e74c3c';
        ctx.lineWidth = 2.5;
        ctx.stroke();
        ctx.restore();
        return;
      }

      if (this.state === 'sleeping') {
        ctx.beginPath();
        ctx.arc(0, -3, 16, 0.15, Math.PI - 0.15);
        ctx.strokeStyle = '#2d3342';
        ctx.lineWidth = 4.5;
        ctx.lineCap = 'round';
        ctx.stroke();
        ctx.restore();
        return;
      }

      const eyeH = 34 * (1 - this.blinkProgress);
      if (eyeH <= 2) {
        ctx.beginPath();
        ctx.moveTo(-18, 0);
        ctx.lineTo(18, 0);
        ctx.strokeStyle = '#2d3342';
        ctx.lineWidth = 4;
        ctx.stroke();
        ctx.restore();
        return;
      }

      ctx.beginPath();
      ctx.ellipse(0, 0, 24, Math.max(eyeH, 4), 0, 0, Math.PI * 2);
      ctx.fillStyle = '#ffffff';
      ctx.fill();
      ctx.strokeStyle = '#2d3342';
      ctx.lineWidth = 3;
      ctx.stroke();

      const px = this.lookX * 8;
      const py = this.lookY * 4.8;

      ctx.save();
      ctx.beginPath();
      ctx.ellipse(0, 0, 24, Math.max(eyeH, 4), 0, 0, Math.PI * 2);
      ctx.clip();

      ctx.beginPath();
      ctx.arc(px, py, 14, 0, Math.PI * 2);
      const irisGrad = ctx.createRadialGradient(px, py, 2, px, py, 14);
      if (this.state === 'spicy') {
        irisGrad.addColorStop(0, '#ffeb3b');
        irisGrad.addColorStop(1, '#d32f2f');
      } else {
        irisGrad.addColorStop(0, '#2ecc71');
        irisGrad.addColorStop(0.7, '#1b8a47');
        irisGrad.addColorStop(1, '#0e552a');
      }
      ctx.fillStyle = irisGrad;
      ctx.fill();

      ctx.beginPath();
      ctx.ellipse(px, py, 6, 11, 0, 0, Math.PI * 2);
      ctx.fillStyle = '#111827';
      ctx.fill();

      ctx.beginPath();
      ctx.arc(px - 4, py - 5, 4, 0, Math.PI * 2);
      ctx.fillStyle = '#ffffff';
      ctx.fill();

      ctx.restore();
      ctx.restore();
    };

    drawEye(-32);
    drawEye(32);

    ctx.beginPath();
    ctx.moveTo(0, 18);
    ctx.lineTo(-8, 11);
    ctx.quadraticCurveTo(0, 9, 8, 11);
    ctx.closePath();
    ctx.fillStyle = '#f472b6';
    ctx.fill();

    const mOpen = this.mouthOpen;
    if (mOpen > 0.08) {
      ctx.beginPath();
      ctx.moveTo(-16, 26);
      ctx.quadraticCurveTo(0, 26 + mOpen * 36, 16, 26);
      ctx.quadraticCurveTo(0, 23, -16, 26);
      ctx.fillStyle = '#881337';
      ctx.fill();
      ctx.strokeStyle = '#3e4352';
      ctx.lineWidth = 2.5;
      ctx.stroke();

      ctx.beginPath();
      ctx.arc(0, 24 + mOpen * 25, 10 * mOpen, 0, Math.PI);
      ctx.fillStyle = '#fb7185';
      ctx.fill();
    } else {
      ctx.beginPath();
      ctx.moveTo(-16, 25);
      ctx.quadraticCurveTo(-8, 33, 0, 25);
      ctx.quadraticCurveTo(8, 33, 16, 25);
      ctx.strokeStyle = '#2d3342';
      ctx.lineWidth = 3;
      ctx.lineCap = 'round';
      ctx.stroke();
    }

    ctx.strokeStyle = '#475569';
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    [[-32, 28, -75, 20], [-32, 34, -78, 35], [-32, 40, -74, 48]].forEach(([x1, y1, x2, y2]) => {
      ctx.beginPath(); ctx.moveTo(x1, y1); ctx.quadraticCurveTo((x1 + x2) / 2, (y1 + y2) / 2 + 3, x2, y2); ctx.stroke();
    });
    [[32, 28, 75, 20], [32, 34, 78, 35], [32, 40, 74, 48]].forEach(([x1, y1, x2, y2]) => {
      ctx.beginPath(); ctx.moveTo(x1, y1); ctx.quadraticCurveTo((x1 + x2) / 2, (y1 + y2) / 2 + 3, x2, y2); ctx.stroke();
    });

    ctx.restore();
  }

  drawPaws(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);

    if (this.state === 'listening') {
      this.drawSinglePaw(ctx, -55, 30, 0);

      ctx.save();
      ctx.translate(65, -80);
      ctx.rotate(-0.8);
      ctx.beginPath();
      ctx.ellipse(0, 0, 22, 16, 0, 0, Math.PI * 2);
      ctx.fillStyle = '#7a8194';
      ctx.fill();
      ctx.strokeStyle = '#3e4352';
      ctx.lineWidth = 3;
      ctx.stroke();
      ctx.restore();
      ctx.restore();
      return;
    }

    this.drawSinglePaw(ctx, -58, 20, 0.3);
    this.drawSinglePaw(ctx, 58, 20, -0.3);
    ctx.restore();
  }

  drawSinglePaw(ctx, px, py, rot) {
    ctx.save();
    ctx.translate(px, py);
    ctx.rotate(rot);
    ctx.beginPath();
    ctx.ellipse(0, 0, 20, 16, 0, 0, Math.PI * 2);
    ctx.fillStyle = '#7a8194';
    ctx.fill();
    ctx.strokeStyle = '#3e4352';
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.fillStyle = '#ff9eb5';
    ctx.beginPath();
    ctx.ellipse(0, 1, 9, 6, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  drawAccessories(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);

    if (this.accessory === 'glasses') {
      ctx.fillStyle = '#111827';
      ctx.strokeStyle = '#f59e0b';
      ctx.lineWidth = 3;
      ctx.beginPath(); ctx.roundRect(-54, -20, 42, 28, 8); ctx.fill(); ctx.stroke();
      ctx.beginPath(); ctx.roundRect(12, -20, 42, 28, 8); ctx.fill(); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(-12, -10); ctx.lineTo(12, -10); ctx.stroke();
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.4)';
      ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.moveTo(-45, -14); ctx.lineTo(-25, 4); ctx.moveTo(20, -14); ctx.lineTo(40, 4); ctx.stroke();
    }
    if (this.accessory === 'party') {
      ctx.save();
      ctx.translate(0, -65);
      ctx.beginPath(); ctx.moveTo(-25, 10); ctx.lineTo(25, 10); ctx.lineTo(0, -60); ctx.closePath();
      ctx.fillStyle = '#ec4899'; ctx.fill(); ctx.strokeStyle = '#facc15'; ctx.lineWidth = 3; ctx.stroke();
      ctx.beginPath(); ctx.arc(0, -62, 8, 0, Math.PI * 2); ctx.fillStyle = '#facc15'; ctx.fill();
      ctx.restore();
    }
    if (this.accessory === 'cowboy') {
      ctx.save();
      ctx.translate(0, -60);
      ctx.beginPath(); ctx.ellipse(0, 5, 65, 14, 0, 0, Math.PI * 2); ctx.fillStyle = '#92400e'; ctx.fill();
      ctx.roundRect(-28, -35, 56, 38, [12, 12, 0, 0]); ctx.fillStyle = '#b45309'; ctx.fill();
      ctx.restore();
    }
    if (this.accessory === 'crown') {
      ctx.save();
      ctx.translate(0, -60);
      ctx.beginPath(); ctx.moveTo(-35, 10); ctx.lineTo(-30, -25); ctx.lineTo(-15, -10); ctx.lineTo(0, -32); ctx.lineTo(15, -10); ctx.lineTo(30, -25); ctx.lineTo(35, 10); ctx.closePath();
      ctx.fillStyle = '#facc15'; ctx.fill(); ctx.strokeStyle = '#ca8a04'; ctx.lineWidth = 3; ctx.stroke();
      [-28, 0, 28].forEach(gx => {
        ctx.beginPath(); ctx.arc(gx, -2, 4, 0, Math.PI * 2); ctx.fillStyle = '#ef4444'; ctx.fill();
      });
      ctx.restore();
    }
    ctx.restore();
  }

  drawBowtie(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(-24, -14); ctx.lineTo(-24, 14); ctx.closePath();
    ctx.fillStyle = '#ef4444'; ctx.fill(); ctx.strokeStyle = '#b91c1c'; ctx.lineWidth = 2.5; ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(24, -14); ctx.lineTo(24, 14); ctx.closePath(); ctx.fill(); ctx.stroke();
    ctx.beginPath(); ctx.arc(0, 0, 6, 0, Math.PI * 2); ctx.fillStyle = '#b91c1c'; ctx.fill();
    ctx.restore();
  }

  drawSoapBubbles(ctx) {
    ctx.save();
    this.soapBubbles.forEach(b => {
      ctx.beginPath(); ctx.arc(b.x, b.y, b.r, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(255, 255, 255, 0.65)'; ctx.fill();
      ctx.strokeStyle = 'rgba(147, 197, 253, 0.9)'; ctx.lineWidth = 1.5; ctx.stroke();
    });
    ctx.restore();
  }

  drawFireBreath(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);
    for (let i = 0; i < 5; i++) {
      const fx = (Math.random() - 0.5) * 50;
      const fy = -Math.random() * 80;
      const fr = 12 + Math.random() * 22;
      ctx.beginPath(); ctx.arc(fx, fy, fr, 0, Math.PI * 2);
      ctx.fillStyle = i % 2 === 0 ? 'rgba(239, 68, 68, 0.75)' : 'rgba(245, 158, 11, 0.85)'; ctx.fill();
    }
    ctx.restore();
  }

  drawZzz(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);
    ctx.fillStyle = '#93c5fd';
    ctx.font = 'bold 26px sans-serif';
    const offset = (Date.now() / 300) % 3;
    ctx.fillText('Z', offset * 10, -offset * 15);
    ctx.font = 'bold 20px sans-serif';
    ctx.fillText('z', offset * 10 + 15, -offset * 15 - 12);
    ctx.restore();
  }
}

window.TomCat = TomCat;
