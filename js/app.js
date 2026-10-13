// Nugfinity - Luxury Mandap Decor, Crystal Jhumars & Royal Event Lighting
// Pure Informational & Event Showcase Logic (Zero E-Commerce / Direct Hotline: 7505045948)
(function () {
  'use strict';

  const WHATSAPP_PHONE = "917505045948";
  const DISPLAY_PHONE = "+91 75050 45948";

  let state = {
    products: PRODUCTS_DATA,
    filteredProducts: PRODUCTS_DATA,
    activeCategory: 'all',
    searchQuery: '',
    selectedQuickProp: null
  };

  const el = {
    productsGrid: document.getElementById('products-grid'),
    productCount: document.getElementById('product-count'),
    categoryPills: document.getElementById('category-pills'),
    searchInput: document.getElementById('search-input'),
    specModal: document.getElementById('spec-modal'),
    specModalContent: document.getElementById('spec-modal-content'),
    mobileMenu: document.getElementById('mobile-menu'),
    mobileMenuBtn: document.getElementById('mobile-menu-btn'),
    closeMobileMenuBtn: document.getElementById('close-mobile-menu-btn'),
    toastContainer: document.getElementById('toast-container'),
    // Auto Draft Elements
    draftClientName: document.getElementById('draft-client-name'),
    draftClientPhone: document.getElementById('draft-client-phone'),
    draftEventDate: document.getElementById('draft-event-date'),
    draftEventCity: document.getElementById('draft-event-city'),
    draftEventType: document.getElementById('draft-event-type'),
    draftRequirement: document.querySelectorAll('input[name="draftRequirement"]'),
    draftNotes: document.getElementById('draft-notes'),
    liveDraftPreview: document.getElementById('live-draft-preview'),
    sendDraftBtn: document.getElementById('send-draft-btn')
  };

  // Toast Notifications
  function showToast(message, type = 'info') {
    if (!el.toastContainer) return;
    const toast = document.createElement('div');
    toast.className = `toast-msg flex items-center gap-3 px-4 py-3 rounded-xl shadow-2xl border text-xs sm:text-sm font-medium backdrop-blur-md ${
      type === 'success' 
        ? 'bg-stone-900/95 text-amber-300 border-amber-500/50 shadow-amber-950/40' 
        : 'bg-stone-900/95 text-stone-200 border-stone-700'
    }`;

    toast.innerHTML = `
      <svg class="w-5 h-5 text-amber-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
      <span>${message}</span>
    `;
    el.toastContainer.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 3200);
  }

  // Render Category Filter Tabs
  function renderCategoryPills() {
    if (!el.categoryPills) return;
    el.categoryPills.innerHTML = CATEGORIES_DATA.map(cat => {
      const isActive = state.activeCategory === cat.id;
      return `
        <button data-category="${cat.id}" class="glass-pill px-4 py-2 rounded-xl text-xs font-semibold whitespace-nowrap flex items-center gap-2 transition ${
          isActive ? 'active' : 'text-stone-300'
        }">
          <span>${cat.label}</span>
          <span class="text-[10px] px-1.5 py-0.5 rounded-full ${isActive ? 'bg-amber-500/20 text-amber-300' : 'bg-white/10 text-stone-400'}">${cat.count}</span>
        </button>
      `;
    }).join('');

    el.categoryPills.querySelectorAll('.glass-pill').forEach(btn => {
      btn.addEventListener('click', (e) => {
        state.activeCategory = e.currentTarget.dataset.category;
        renderCategoryPills();
        filterAndRenderProducts();
      });
    });
  }

  // Filter Products
  function filterAndRenderProducts() {
    let result = [...state.products];

    if (state.activeCategory !== 'all') {
      result = result.filter(p => p.category === state.activeCategory);
    }

    if (state.searchQuery.trim() !== '') {
      const q = state.searchQuery.toLowerCase().trim();
      result = result.filter(p => 
        p.name.toLowerCase().includes(q) ||
        p.hindiName.toLowerCase().includes(q) ||
        p.material.toLowerCase().includes(q) ||
        p.propType.toLowerCase().includes(q) ||
        p.idealFor.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q)
      );
    }

    state.filteredProducts = result;
    if (el.productCount) {
      el.productCount.textContent = `${result.length} Grand Props Available`;
    }

    renderProductsGrid(result);
  }

  // Render Product Showcase Cards
  function renderProductsGrid(products) {
    if (!el.productsGrid) return;

    if (products.length === 0) {
      el.productsGrid.innerHTML = `
        <div class="col-span-full py-16 text-center glass-panel rounded-3xl p-8">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-amber-500/10 text-amber-400 flex items-center justify-center border border-amber-500/20">
            <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
          </div>
          <h3 class="font-cinzel text-xl text-white font-bold mb-2">No Props Found</h3>
          <p class="text-xs text-stone-400 max-w-md mx-auto mb-5">Try clearing your search terms to explore all crystal jhumars, royal sofas, and tree lamps.</p>
          <button onclick="window.NUG.resetFilters()" class="px-6 py-2.5 rounded-xl gold-gradient-btn text-stone-950 font-bold text-xs uppercase tracking-wider">
            View All Grand Decor
          </button>
        </div>
      `;
      return;
    }

    el.productsGrid.innerHTML = products.map(item => {
      return `
        <div class="showcase-card rounded-2xl overflow-hidden flex flex-col group relative" data-product-id="${item.id}">
          
          <!-- Image Section with Guaranteed Fallback (Zero missing images) -->
          <div class="img-wrap-luxury aspect-[4/3] cursor-pointer" onclick="window.NUG.openSpecModal('${item.id}')">
            <img 
              src="${item.image}" 
              alt="${item.name}" 
              loading="lazy"
              onerror="this.onerror=null; this.src='${item.fallback}';"
              class="w-full h-full object-cover img-zoom-hover"
            />
            
            <div class="absolute inset-0 bg-gradient-to-t from-stone-950/90 via-stone-950/20 to-transparent pointer-events-none"></div>

            <!-- Top Badges -->
            <div class="absolute top-3 left-3 flex flex-col gap-1.5 z-10 pointer-events-none">
              <span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase bg-amber-500 text-stone-950 shadow-md">
                ${item.badge}
              </span>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-medium bg-black/60 backdrop-blur-md text-amber-200 border border-white/10">
                ${item.propType}
              </span>
            </div>

            <!-- Dimensions / Scale Badge -->
            <div class="absolute bottom-3 left-3 right-3 flex items-center justify-between text-[11px] pointer-events-none">
              <span class="bg-black/70 backdrop-blur-md px-2.5 py-1 rounded-lg border border-white/10 text-stone-200 font-mono">
                📏 ${item.height}
              </span>
              <span class="bg-amber-950/80 backdrop-blur-md px-2.5 py-1 rounded-lg border border-amber-500/30 text-amber-300 font-semibold text-[10px]">
                ${item.availability}
              </span>
            </div>
          </div>

          <!-- Content Info -->
          <div class="p-5 flex-1 flex flex-col justify-between">
            <div>
              <div class="text-[10px] uppercase font-bold tracking-widest text-amber-400 mb-1">
                ${item.categoryLabel}
              </div>

              <h3 class="font-cinzel text-base font-bold text-white leading-snug group-hover:text-amber-300 transition cursor-pointer mb-1 line-clamp-1" onclick="window.NUG.openSpecModal('${item.id}')">
                ${item.name}
              </h3>

              <p class="text-xs text-amber-200/60 italic mb-2 font-medium">${item.hindiName}</p>

              <p class="text-xs text-stone-300 line-clamp-2 mb-4 leading-relaxed">
                ${item.description}
              </p>

              <!-- Quick Spec Chips -->
              <div class="grid grid-cols-2 gap-2 text-[11px] mb-5">
                <div class="spec-chip p-2 rounded-lg text-stone-300">
                  <span class="text-stone-500 block text-[9px] uppercase font-bold">Span / Width:</span>
                  <span class="font-medium text-white truncate block">${item.diameter}</span>
                </div>
                <div class="spec-chip p-2 rounded-lg text-stone-300">
                  <span class="text-stone-500 block text-[9px] uppercase font-bold">Lighting / Rig:</span>
                  <span class="font-medium text-amber-300 truncate block">${item.lightsCount}</span>
                </div>
              </div>
            </div>

            <!-- Informational Action Buttons (No Buy / No Cart) -->
            <div class="grid grid-cols-2 gap-2 pt-2 border-t border-white/10">
              <button 
                type="button" 
                onclick="window.NUG.openSpecModal('${item.id}')"
                class="w-full py-2.5 px-2 rounded-xl glass-pill text-white text-xs font-semibold tracking-wide transition flex items-center justify-center gap-1.5"
              >
                <span>Full Specs</span>
                <svg class="w-3.5 h-3.5 text-stone-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>
              </button>

              <button 
                type="button" 
                onclick="window.NUG.inquirePropWhatsApp('${item.id}')"
                class="w-full py-2.5 px-2 rounded-xl wa-neon-btn text-white text-xs font-bold tracking-wide transition flex items-center justify-center gap-1.5 shadow"
                title="Inquire on WhatsApp 7505045948"
              >
                <svg class="w-3.5 h-3.5 fill-current" viewBox="0 0 24 24"><path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91C2.13 13.66 2.59 15.36 3.45 16.86L2.05 22L7.3 20.62C8.75 21.41 10.38 21.83 12.04 21.83C17.5 21.83 21.95 17.38 21.95 11.92C21.95 6.46 17.5 2 12.04 2M12.05 3.67C16.58 3.67 20.28 7.37 20.28 11.9C20.28 16.43 16.58 20.13 12.05 20.13C10.59 20.13 9.17 19.74 7.94 19.01L7.63 18.82L4.54 19.63L5.37 16.62L5.16 16.29C4.36 15.01 3.93 13.48 3.93 11.9C3.93 7.37 7.63 3.67 12.05 3.67M9.27 7.42C9.09 7.42 8.89 7.48 8.71 7.69C8.53 7.9 8.01 8.39 8.01 9.4C8.01 10.41 8.74 11.38 8.85 11.53C8.96 11.68 10.28 13.72 12.3 14.6C12.78 14.81 13.16 14.93 13.45 15.02C13.93 15.17 14.37 15.15 14.72 15.1C15.11 15.04 15.92 14.61 16.08 14.15C16.25 13.69 16.25 13.3 16.2 13.22C16.15 13.14 16.03 13.1 15.85 13.01C15.67 12.92 14.77 12.48 14.6 12.42C14.43 12.36 14.31 12.33 14.19 12.51C14.07 12.69 13.72 13.1 13.61 13.22C13.5 13.34 13.39 13.36 13.21 13.27C13.03 13.18 12.27 12.93 11.36 12.12C10.66 11.49 10.18 10.72 10.05 10.5C9.92 10.28 10.04 10.16 10.13 10.07C10.21 9.99 10.31 9.86 10.41 9.74C10.51 9.62 10.54 9.53 10.6 9.41C10.66 9.29 10.63 9.18 10.58 9.09C10.53 9 10.08 7.89 9.89 7.45C9.7 7.03 9.51 7.08 9.38 7.08C9.25 7.08 9.09 7.08 8.93 7.08"/></svg>
                <span>WhatsApp</span>
              </button>
            </div>
          </div>
        </div>
      `;
    }).join('');
  }

  // Informational Specification Modal
  function openSpecModal(productId) {
    const product = state.products.find(p => p.id === productId);
    if (!product || !el.specModal || !el.specModalContent) return;
    state.selectedQuickProp = product;

    el.specModalContent.innerHTML = `
      <div class="grid grid-cols-1 md:grid-cols-2 gap-8 items-start">
        <!-- Visual Presentation -->
        <div class="space-y-4">
          <div class="img-wrap-luxury aspect-square rounded-2xl overflow-hidden relative border border-white/10">
            <img 
              src="${product.image}" 
              alt="${product.name}" 
              onerror="this.onerror=null; this.src='${product.fallback}';"
              class="w-full h-full object-cover"
            />
            <div class="absolute top-3 left-3">
              <span class="px-3 py-1 rounded-full text-xs font-bold uppercase bg-amber-500 text-stone-950">
                ${product.badge}
              </span>
            </div>
          </div>
          <div class="p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-200">
            <strong>🎪 Staging Suitability:</strong> ${product.idealFor}
          </div>
        </div>

        <!-- Specifications & Inquiry -->
        <div class="space-y-5">
          <div>
            <div class="text-xs uppercase font-bold text-amber-400 tracking-widest mb-1">${product.categoryLabel}</div>
            <h2 class="font-cinzel text-2xl font-bold text-white mb-1">${product.name}</h2>
            <p class="text-xs text-amber-300/70 italic mb-3">${product.hindiName}</p>
            <p class="text-xs text-stone-300 leading-relaxed">${product.description}</p>
          </div>

          <!-- Technical Specs Table -->
          <div class="bg-black/60 rounded-xl p-4 border border-white/10 space-y-2 text-xs">
            <h4 class="font-cinzel font-bold text-amber-400 text-xs border-b border-white/10 pb-1.5">Technical & Staging Specifications</h4>
            ${product.specifications.map(s => `
              <div class="flex justify-between py-1 border-b border-white/[0.05]">
                <span class="text-stone-400 font-medium">${s.label}:</span>
                <span class="text-stone-100 font-semibold text-right">${s.val}</span>
              </div>
            `).join('')}
          </div>

          <!-- Key Advantages -->
          <div>
            <h4 class="font-cinzel font-bold text-white text-xs mb-2">Key Production Features:</h4>
            <ul class="space-y-1.5 text-xs text-stone-300">
              ${product.highlights.map(h => `
                <li class="flex items-center gap-2">
                  <span class="text-amber-400">✦</span>
                  <span>${h}</span>
                </li>
              `).join('')}
            </ul>
          </div>

          <!-- Direct WhatsApp Consultation Button -->
          <div class="pt-4 border-t border-white/10">
            <button 
              type="button" 
              onclick="window.NUG.inquirePropWhatsApp('${product.id}')"
              class="w-full py-3.5 px-4 rounded-xl wa-neon-btn text-white font-black text-xs uppercase tracking-wider flex items-center justify-center gap-2"
            >
              <svg class="w-4 h-4 fill-current" viewBox="0 0 24 24"><path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91C2.13 13.66 2.59 15.36 3.45 16.86L2.05 22L7.3 20.62C8.75 21.41 10.38 21.83 12.04 21.83C17.5 21.83 21.95 17.38 21.95 11.92C21.95 6.46 17.5 2 12.04 2M12.05 3.67C16.58 3.67 20.28 7.37 20.28 11.9C20.28 16.43 16.58 20.13 12.05 20.13C10.59 20.13 9.17 19.74 7.94 19.01L7.63 18.82L4.54 19.63L5.37 16.62L5.16 16.29C4.36 15.01 3.93 13.48 3.93 11.9C3.93 7.37 7.63 3.67 12.05 3.67M9.27 7.42C9.09 7.42 8.89 7.48 8.71 7.69C8.53 7.9 8.01 8.39 8.01 9.4C8.01 10.41 8.74 11.38 8.85 11.53C8.96 11.68 10.28 13.72 12.3 14.6C12.78 14.81 13.16 14.93 13.45 15.02C13.93 15.17 14.37 15.15 14.72 15.1C15.11 15.04 15.92 14.61 16.08 14.15C16.25 13.69 16.25 13.3 16.2 13.22C16.15 13.14 16.03 13.1 15.85 13.01C15.67 12.92 14.77 12.48 14.6 12.42C14.43 12.36 14.31 12.33 14.19 12.51C14.07 12.69 13.72 13.1 13.61 13.22C13.5 13.34 13.39 13.36 13.21 13.27C13.03 13.18 12.27 12.93 11.36 12.12C10.66 11.49 10.18 10.72 10.05 10.5C9.92 10.28 10.04 10.16 10.13 10.07C10.21 9.99 10.31 9.86 10.41 9.74C10.51 9.62 10.54 9.53 10.6 9.41C10.66 9.29 10.63 9.18 10.58 9.09C10.53 9 10.08 7.89 9.89 7.45C9.7 7.03 9.51 7.08 9.38 7.08C9.25 7.08 9.09 7.08 8.93 7.08"/></svg>
              <span>Inquire on WhatsApp Hotline: 7505045948</span>
            </button>
          </div>
        </div>
      </div>
    `;

    el.specModal.classList.remove('hidden');
    document.body.classList.add('overflow-hidden');
  }

  function closeSpecModal() {
    if (el.specModal) {
      el.specModal.classList.add('hidden');
      document.body.classList.remove('overflow-hidden');
    }
  }

  // Single Item Direct WhatsApp Inquiry
  function inquirePropWhatsApp(productId) {
    const product = state.products.find(p => p.id === productId);
    if (!product) return;

    const message = `Namaste Nugfinity Team! 🙏\n\nI am inquiring about this Grand Event Prop from your catalog:\n✨ *${product.name}*\n📐 Dimensions: ${product.height} x ${product.diameter}\n🎪 Suitable For: ${product.idealFor}\n💡 Rigging Specs: ${product.lightsCount}\n\nPlease share booking rates (Rental / Direct Purchase) and slot availability for our upcoming event date.\nThank you!`;

    const url = `https://wa.me/${WHATSAPP_PHONE}?text=${encodeURIComponent(message)}`;
    window.open(url, '_blank');
  }

  // ================= AUTOMATIC WHATSAPP MESSAGE DRAFT GENERATOR =================
  function generateDraftMessage() {
    const name = el.draftClientName ? el.draftClientName.value.trim() : '';
    const phone = el.draftClientPhone ? el.draftClientPhone.value.trim() : '';
    const date = el.draftEventDate ? el.draftEventDate.value : '';
    const city = el.draftEventCity ? el.draftEventCity.value.trim() : '';
    const eventType = el.draftEventType ? el.draftEventType.value : 'Grand Royal Wedding Mandap';

    let reqType = 'Event Rental & On-Site Rigging';
    document.querySelectorAll('input[name="draftRequirement"]').forEach(r => {
      if (r.checked) reqType = r.value;
    });

    const selectedProps = [];
    document.querySelectorAll('.draft-prop-checkbox:checked').forEach(cb => {
      selectedProps.push(cb.value);
    });

    const notes = el.draftNotes ? el.draftNotes.value.trim() : '';

    let lines = [];
    lines.push(`Namaste Nugfinity Team! 🙏`);
    lines.push(`I would like to inquire about Grand Event & Mandap Decor props for our upcoming celebration:`);
    lines.push(``);
    lines.push(`👤 *Client Name:* ${name || '[Your Name]'}`);
    lines.push(`📞 *Contact Phone:* ${phone || '[Your Phone]'}`);
    lines.push(`🎪 *Event Type:* ${eventType}`);
    lines.push(`📅 *Event Date:* ${date || '[Select Date]'}`);
    lines.push(`📍 *Venue / City:* ${city || '[Your City / Venue]'}`);
    lines.push(`🛠️ *Requirement:* ${reqType}`);
    lines.push(``);

    if (selectedProps.length > 0) {
      lines.push(`💎 *Decor Props Selected:*`);
      selectedProps.forEach(p => lines.push(`  • ${p}`));
    } else {
      lines.push(`💎 *Decor Props Selected:* [Select props from checkboxes]`);
    }

    if (notes) {
      lines.push(``);
      lines.push(`📝 *Special Notes:* ${notes}`);
    }

    lines.push(``);
    lines.push(`Please share the official Nugfinity decor catalog, slot availability, and quote.`);
    lines.push(`Thank you!`);

    const fullMessage = lines.join('\n');

    if (el.liveDraftPreview) {
      el.liveDraftPreview.textContent = fullMessage;
    }

    return fullMessage;
  }

  function handleSendAutoDraft(e) {
    if (e) e.preventDefault();
    const message = generateDraftMessage();
    const url = `https://wa.me/${WHATSAPP_PHONE}?text=${encodeURIComponent(message)}`;
    window.open(url, '_blank');
    showToast('Drafted inquiry opened in WhatsApp!', 'success');
  }

  function resetFilters() {
    state.activeCategory = 'all';
    state.searchQuery = '';
    if (el.searchInput) el.searchInput.value = '';
    renderCategoryPills();
    filterAndRenderProducts();
  }

  // Render Testimonials
  function renderTestimonials() {
    const container = document.getElementById('testimonials-grid');
    if (!container) return;

    container.innerHTML = TESTIMONIALS_DATA.map(t => `
      <div class="showcase-card rounded-2xl p-6 flex flex-col justify-between">
        <div>
          <div class="flex items-center gap-1 text-amber-400 mb-3">
            ${Array(t.rating).fill('<svg class="w-4 h-4 fill-current" viewBox="0 0 20 20"><path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/></svg>').join('')}
          </div>
          <h4 class="font-cinzel font-bold text-white text-sm mb-2 leading-snug">"${t.title}"</h4>
          <p class="text-xs text-stone-300 leading-relaxed mb-4">${t.comment}</p>
          <div class="text-[11px] text-amber-300 font-medium mb-4 bg-amber-500/10 p-2 rounded-lg border border-amber-500/20">
            Booked: ${t.product}
          </div>
        </div>
        <div class="flex items-center gap-3 pt-3 border-t border-white/10">
          <img src="${t.avatar}" alt="${t.name}" class="w-10 h-10 rounded-full object-cover border border-amber-500/40"/>
          <div>
            <h5 class="text-xs font-bold text-white">${t.name}</h5>
            <span class="text-[10px] text-stone-400">${t.role} • ${t.city}</span>
          </div>
        </div>
      </div>
    `).join('');
  }

  // Event Listeners
  function initEventListeners() {
    if (el.searchInput) {
      el.searchInput.addEventListener('input', (e) => {
        state.searchQuery = e.target.value;
        filterAndRenderProducts();
      });
    }

    if (el.mobileMenuBtn && el.mobileMenu) {
      el.mobileMenuBtn.addEventListener('click', () => el.mobileMenu.classList.remove('hidden'));
    }
    if (el.closeMobileMenuBtn && el.mobileMenu) {
      el.closeMobileMenuBtn.addEventListener('click', () => el.mobileMenu.classList.add('hidden'));
    }

    if (el.specModal) {
      el.specModal.addEventListener('click', (e) => {
        if (e.target === el.specModal) closeSpecModal();
      });
    }

    // Auto-draft inputs
    const draftInputs = [
      el.draftClientName,
      el.draftClientPhone,
      el.draftEventDate,
      el.draftEventCity,
      el.draftEventType,
      el.draftNotes
    ];
    draftInputs.forEach(inp => {
      if (inp) {
        inp.addEventListener('input', generateDraftMessage);
        inp.addEventListener('change', generateDraftMessage);
      }
    });

    document.querySelectorAll('input[name="draftRequirement"]').forEach(r => {
      r.addEventListener('change', generateDraftMessage);
    });

    document.querySelectorAll('.draft-prop-checkbox').forEach(cb => {
      cb.addEventListener('change', generateDraftMessage);
    });

    if (el.sendDraftBtn) {
      el.sendDraftBtn.addEventListener('click', handleSendAutoDraft);
    }

    const draftForm = document.getElementById('auto-draft-form');
    if (draftForm) {
      draftForm.addEventListener('submit', handleSendAutoDraft);
    }
  }

  // Public Global API
  window.NUG = {
    openSpecModal,
    closeSpecModal,
    inquirePropWhatsApp,
    resetFilters,
    generateDraftMessage,
    handleSendAutoDraft
  };

  function init() {
    renderCategoryPills();
    filterAndRenderProducts();
    renderTestimonials();
    initEventListeners();
    generateDraftMessage();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
