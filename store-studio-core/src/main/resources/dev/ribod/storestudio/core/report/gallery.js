(function() {
  'use strict';

  var currentTheme = localStorage.getItem('store-studio-theme') ||
    (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
  document.documentElement.setAttribute('data-theme', currentTheme);

  var themeToggleBtn = document.getElementById('theme-toggle');
  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', function() {
      var isLight = document.documentElement.getAttribute('data-theme') === 'light';
      var next = isLight ? 'dark' : 'light';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('store-studio-theme', next);
    });
  }

  var STORE_STRINGS = {};
  var APP_METADATA = {};
  try {
    var catEl = document.getElementById('store-catalog-data');
    if (catEl) STORE_STRINGS = JSON.parse(catEl.textContent);
    var metaEl = document.getElementById('store-metadata-data');
    if (metaEl) APP_METADATA = JSON.parse(metaEl.textContent);
  } catch (e) {}

  function resolveLocale(loc) {
    if (!loc || loc === 'ALL') return 'en-US';
    if (STORE_STRINGS[loc]) return loc;
    if (loc.indexOf('es') === 0 && STORE_STRINGS['es-MX']) return 'es-MX';
    return 'en-US';
  }

  function getNested(obj, path) {
    var parts = path.split('.');
    var curr = obj;
    for (var i = 0; i < parts.length; i++) {
      if (!curr) return undefined;
      curr = curr[parts[i]];
    }
    return curr;
  }

  function applyLocalization(loc) {
    var target = resolveLocale(loc);
    var dict = STORE_STRINGS[target] || STORE_STRINGS['en-US'] || {};
    var meta = (APP_METADATA && (APP_METADATA[loc] || APP_METADATA[target])) || {};

    document.querySelectorAll('[data-i18n]').forEach(function(el) {
      var key = el.getAttribute('data-i18n');
      var val = getNested(dict, key);
      if (val !== undefined && val !== null) {
        var svg = el.querySelector('svg');
        if (svg) {
          el.replaceChildren(svg, document.createTextNode(' ' + val));
        } else {
          el.textContent = val;
        }
      }
    });

    document.querySelectorAll('[data-meta]').forEach(function(el) {
      var field = el.getAttribute('data-meta');
      if (meta && meta[field]) {
        el.textContent = meta[field];
      }
    });
  }

  var activeStore = 'ALL';
  var activeDevice = 'ALL';
  var activeLocale = 'ALL';
  var activeSearch = '';

  var cards = document.querySelectorAll('.asset-card');

  function updateFilters() {
    var query = activeSearch.toLowerCase().trim();
    cards.forEach(function(card) {
      var store = card.getAttribute('data-store') || '';
      var device = card.getAttribute('data-device') || '';
      var locale = card.getAttribute('data-locale') || '';
      var slotKey = (card.getAttribute('data-slot-key') || '').toLowerCase();
      var id = (card.getAttribute('data-id') || '').toLowerCase();

      var matchStore = (activeStore === 'ALL' || store === activeStore);
      var matchDevice = (activeDevice === 'ALL' || device === activeDevice);
      var matchLocale = (activeLocale === 'ALL' || locale === activeLocale);
      var matchSearch = (!query || slotKey.indexOf(query) !== -1 || id.indexOf(query) !== -1);

      if (matchStore && matchDevice && matchLocale && matchSearch) {
        card.style.display = 'flex';
      } else {
        card.style.display = 'none';
      }
    });

    updatePreviewLocale(activeLocale);
  }

  function updatePreviewLocale(loc) {
    document.querySelectorAll('.play-screenshot-item, .apple-screenshot-item').forEach(
      function(item) {
        var itemLoc = item.getAttribute('data-locale');
        var match = (loc === 'ALL' || itemLoc === loc);
        var slotMatched = item.getAttribute('data-slot-visible') !== 'false';
        item.style.display = (match && slotMatched) ? 'block' : 'none';
      }
    );

    document.querySelectorAll('.play-feature-banner').forEach(function(banner) {
      var bannerLoc = banner.getAttribute('data-locale');
      if (loc === 'ALL') {
        var isPrimary = banner.getAttribute('data-is-primary') === 'true';
        banner.style.display = isPrimary ? 'block' : 'none';
      } else {
        banner.style.display = (bannerLoc === loc) ? 'block' : 'none';
      }
    });

    applyLocalization(loc);
  }

  document.querySelectorAll('.store-filter-pill').forEach(function(btn) {
    btn.addEventListener('click', function() {
      document.querySelectorAll('.store-filter-pill').forEach(function(b) {
        b.classList.remove('active');
      });
      btn.classList.add('active');
      activeStore = btn.getAttribute('data-val');
      updateFilters();
    });
  });

  document.querySelectorAll('.device-filter-pill').forEach(function(btn) {
    btn.addEventListener('click', function() {
      document.querySelectorAll('.device-filter-pill').forEach(function(b) {
        b.classList.remove('active');
      });
      btn.classList.add('active');
      activeDevice = btn.getAttribute('data-val');
      updateFilters();
    });
  });

  var localeSelect = document.getElementById('locale-select');
  var previewLocaleSelect = document.getElementById('preview-section-locale');

  function syncLocale(newVal) {
    activeLocale = newVal;
    if (localeSelect && localeSelect.value !== newVal) {
      localeSelect.value = newVal;
    }
    if (previewLocaleSelect && previewLocaleSelect.value !== newVal) {
      previewLocaleSelect.value = newVal;
    }
    updateFilters();
  }

  if (localeSelect) {
    if (localeSelect.options.length > 2) {
      localeSelect.selectedIndex = 1;
      activeLocale = localeSelect.value;
      if (previewLocaleSelect) previewLocaleSelect.value = activeLocale;
    }
    localeSelect.addEventListener('change', function() {
      syncLocale(localeSelect.value);
    });
  }

  if (previewLocaleSelect) {
    previewLocaleSelect.addEventListener('change', function() {
      syncLocale(previewLocaleSelect.value);
    });
  }

  var searchInput = document.getElementById('search-input');
  if (searchInput) {
    searchInput.addEventListener('input', function() {
      activeSearch = searchInput.value;
      updateFilters();
    });
  }

  document.querySelectorAll('.play-device-tab').forEach(function(tab) {
    tab.addEventListener('click', function() {
      document.querySelectorAll('.play-device-tab').forEach(function(t) {
        t.classList.remove('active');
      });
      tab.classList.add('active');
      var slot = tab.getAttribute('data-slot');
      document.querySelectorAll('.play-screenshot-item').forEach(function(item) {
        var itemSlot = item.getAttribute('data-slot');
        var matchSlot = (slot === 'ALL' || itemSlot === slot);
        item.setAttribute('data-slot-visible', matchSlot ? 'true' : 'false');
        var itemLoc = item.getAttribute('data-locale');
        var matchLoc = (activeLocale === 'ALL' || itemLoc === activeLocale);
        item.style.display = (matchSlot && matchLoc) ? 'block' : 'none';
      });
    });
  });

  document.querySelectorAll('.apple-device-tab').forEach(function(tab) {
    tab.addEventListener('click', function() {
      document.querySelectorAll('.apple-device-tab').forEach(function(t) {
        t.classList.remove('active');
      });
      tab.classList.add('active');
      var slot = tab.getAttribute('data-slot');
      document.querySelectorAll('.apple-screenshot-item').forEach(function(item) {
        var itemSlot = item.getAttribute('data-slot');
        var matchSlot = (slot === 'ALL' || itemSlot === slot);
        item.setAttribute('data-slot-visible', matchSlot ? 'true' : 'false');
        var itemLoc = item.getAttribute('data-locale');
        var matchLoc = (activeLocale === 'ALL' || itemLoc === activeLocale);
        item.style.display = (matchSlot && matchLoc) ? 'block' : 'none';
      });
    });
  });

  var modal = document.getElementById('lightbox-modal');
  var modalImg = document.getElementById('modal-img');
  var modalSlot = document.getElementById('modal-slot');
  var modalTarget = document.getElementById('modal-target');
  var modalLocale = document.getElementById('modal-locale');
  var modalDims = document.getElementById('modal-dims');
  var modalFormat = document.getElementById('modal-format');
  var modalSha = document.getElementById('modal-sha');
  var modalPhash = document.getElementById('modal-phash');
  var modalLink = document.getElementById('modal-fullres-link');

  cards.forEach(function(card) {
    card.addEventListener('click', function() {
      if (!modal) return;
      var src = card.getAttribute('data-uri');
      var fullRes = card.getAttribute('data-fullres');
      modalImg.src = src;
      modalSlot.textContent = card.getAttribute('data-slot-key');
      modalTarget.textContent = card.getAttribute('data-store');
      modalLocale.textContent = card.getAttribute('data-locale');
      modalDims.textContent = card.getAttribute('data-dims');
      modalFormat.textContent = card.getAttribute('data-format');
      modalSha.textContent = card.getAttribute('data-sha');
      modalPhash.textContent = card.getAttribute('data-phash');
      if (modalLink) {
        modalLink.href = fullRes;
        modalLink.style.display = 'inline-block';
      }
      modal.classList.add('active');
    });
  });

  var closeBtn = document.getElementById('modal-close');
  if (closeBtn) {
    closeBtn.addEventListener('click', function() {
      if (modal) modal.classList.remove('active');
    });
  }

  if (modal) {
    modal.addEventListener('click', function(e) {
      if (e.target === modal) {
        modal.classList.remove('active');
      }
    });
  }

  window.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && modal && modal.classList.contains('active')) {
      modal.classList.remove('active');
    }
  });

  var viewTabs = document.querySelectorAll('.view-tab');
  var gallerySection = document.getElementById('gallery-section');
  var previewSection = document.getElementById('preview-section');

  viewTabs.forEach(function(tab) {
    tab.addEventListener('click', function() {
      var view = tab.getAttribute('data-view');
      viewTabs.forEach(function(t) { t.classList.remove('active'); });
      tab.classList.add('active');

      if (view === 'preview') {
        if (gallerySection) gallerySection.style.display = 'none';
        if (previewSection) previewSection.style.display = 'block';
      } else {
        if (gallerySection) gallerySection.style.display = 'block';
        if (previewSection) previewSection.style.display = 'none';
      }
    });
  });

  var storeTabs = document.querySelectorAll('.store-btn');
  var playMockup = document.getElementById('play-mockup');
  var appleMockup = document.getElementById('apple-mockup');

  storeTabs.forEach(function(btn) {
    btn.addEventListener('click', function() {
      var store = btn.getAttribute('data-store-target');
      storeTabs.forEach(function(b) { b.classList.remove('active'); });
      btn.classList.add('active');

      if (store === 'APP_STORE') {
        if (playMockup) playMockup.style.display = 'none';
        if (appleMockup) appleMockup.style.display = 'block';
      } else {
        if (playMockup) playMockup.style.display = 'block';
        if (appleMockup) appleMockup.style.display = 'none';
      }
    });
  });

  updateFilters();
})();
