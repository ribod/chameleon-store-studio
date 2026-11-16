(function () {
  'use strict';

  function initTheme() {
    var stored = null;
    try {
      stored = localStorage.getItem('store-studio-theme');
    } catch (e) {}

    var theme = stored || 'dark';
    document.documentElement.setAttribute('data-theme', theme);

    var toggleBtn = document.getElementById('theme-toggle');
    if (toggleBtn) {
      toggleBtn.addEventListener('click', function () {
        var current = document.documentElement.getAttribute('data-theme');
        var next = current === 'light' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', next);
        try {
          localStorage.setItem('store-studio-theme', next);
        } catch (e) {}
      });
    }
  }

  function initViewModes() {
    var cards = document.querySelectorAll('.diff-card');
    cards.forEach(function (card) {
      var buttons = card.querySelectorAll('.viewer-mode-btn');
      var stages = card.querySelectorAll('.viewer-stage-content');

      buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
          var targetMode = btn.getAttribute('data-mode');
          buttons.forEach(function (b) { b.classList.remove('active'); });
          btn.classList.add('active');

          stages.forEach(function (stage) {
            if (stage.getAttribute('data-mode') === targetMode) {
              stage.style.display = 'flex';
            } else {
              stage.style.display = 'none';
            }
          });
        });
      });

      var sliderStage = card.querySelector('.viewer-stage-content[data-mode="slider"]');
      var sliderContainer = sliderStage ? sliderStage.querySelector('.stage-slider-container') : null;
      var sliderInput = sliderStage ? sliderStage.querySelector('.stage-slider-input') : null;

      if (sliderContainer) {
        var isDragging = false;

        function setSplitPosition(clientX) {
          var rect = sliderContainer.getBoundingClientRect();
          if (rect.width <= 0) return;
          var offsetX = clientX - rect.left;
          var pct = (offsetX / rect.width) * 100;
          pct = Math.max(0, Math.min(100, pct));
          var rounded = Math.round(pct * 10) / 10;
          sliderContainer.style.setProperty('--split-pos', rounded + '%');
          if (sliderInput) {
            sliderInput.value = rounded;
          }
        }

        sliderContainer.addEventListener('mousedown', function (e) {
          isDragging = true;
          setSplitPosition(e.clientX);
          e.preventDefault();
        });

        window.addEventListener('mousemove', function (e) {
          if (isDragging) {
            setSplitPosition(e.clientX);
          }
        });

        window.addEventListener('mouseup', function () {
          isDragging = false;
        });

        sliderContainer.addEventListener('touchstart', function (e) {
          if (e.touches && e.touches.length > 0) {
            isDragging = true;
            setSplitPosition(e.touches[0].clientX);
          }
        }, { passive: true });

        window.addEventListener('touchmove', function (e) {
          if (isDragging && e.touches && e.touches.length > 0) {
            setSplitPosition(e.touches[0].clientX);
          }
        }, { passive: true });

        window.addEventListener('touchend', function () {
          isDragging = false;
        });

        if (sliderInput) {
          sliderInput.addEventListener('input', function (e) {
            sliderContainer.style.setProperty('--split-pos', e.target.value + '%');
          });
        }
      }

      var onionStage = card.querySelector('.viewer-stage-content[data-mode="onion"]');
      var onionContainer = onionStage ? onionStage.querySelector('.stage-onion-container') : null;
      var onionInput = onionStage ? onionStage.querySelector('.stage-onion-input') : null;
      var onionLabel = onionStage ? onionStage.querySelector('.stage-onion-label') : null;

      if (onionInput && onionContainer) {
        onionInput.addEventListener('input', function (e) {
          var val = e.target.value;
          onionContainer.style.setProperty('--onion-opacity', val);
          if (onionLabel) {
            onionLabel.textContent = 'Opacity: ' + Math.round(val * 100) + '%';
          }
        });
      }
    });
  }

  function initFilters() {
    var currentType = 'ALL';
    var currentStore = 'ALL';
    var currentDevice = 'ALL';
    var searchQuery = '';

    var typeChips = document.querySelectorAll('.stat-chip');
    var storeButtons = document.querySelectorAll('.filter-btn[data-store]');
    var deviceButtons = document.querySelectorAll('.filter-btn[data-device]');
    var searchInput = document.getElementById('search-input');
    var cards = document.querySelectorAll('.diff-card');
    var bucketSections = document.querySelectorAll('.bucket-section');

    function applyFilters() {
      var visibleTotal = 0;

      bucketSections.forEach(function (section) {
        var bucketTarget = section.getAttribute('data-target') || '';
        var bucketSlot = section.getAttribute('data-slot') || '';
        var sectionVisibleCount = 0;
        var sectionCards = section.querySelectorAll('.diff-card');

        sectionCards.forEach(function (card) {
          var cardType = card.getAttribute('data-type') || '';
          var cardId = (card.getAttribute('data-id') || '').toLowerCase();
          var cardBucket = (card.getAttribute('data-bucket') || '').toLowerCase();

          var matchesType = (currentType === 'ALL' || cardType === currentType);
          var matchesStore = (currentStore === 'ALL' || bucketTarget === currentStore);
          var matchesDevice = (currentDevice === 'ALL' || bucketSlot === currentDevice);
          var matchesSearch = !searchQuery ||
            cardId.indexOf(searchQuery) !== -1 ||
            cardBucket.indexOf(searchQuery) !== -1;

          if (matchesType && matchesStore && matchesDevice && matchesSearch) {
            card.style.display = '';
            sectionVisibleCount++;
            visibleTotal++;
          } else {
            card.style.display = 'none';
          }
        });

        if (sectionVisibleCount === 0) {
          section.style.display = 'none';
        } else {
          section.style.display = '';
        }
      });

      var emptyBanner = document.getElementById('diff-empty-banner');
      if (emptyBanner) {
        emptyBanner.style.display = visibleTotal === 0 ? 'block' : 'none';
      }
    }

    typeChips.forEach(function (chip) {
      chip.addEventListener('click', function () {
        var filterVal = chip.getAttribute('data-filter');
        typeChips.forEach(function (c) { c.classList.remove('active'); });
        if (currentType === filterVal) {
          currentType = 'ALL';
        } else {
          currentType = filterVal;
          chip.classList.add('active');
        }
        applyFilters();
      });
    });

    storeButtons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        storeButtons.forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        currentStore = btn.getAttribute('data-store');
        applyFilters();
      });
    });

    deviceButtons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        deviceButtons.forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        currentDevice = btn.getAttribute('data-device');
        applyFilters();
      });
    });

    if (searchInput) {
      searchInput.addEventListener('input', function (e) {
        searchQuery = (e.target.value || '').trim().toLowerCase();
        applyFilters();
      });
    }

    applyFilters();
  }

  document.addEventListener('DOMContentLoaded', function () {
    initTheme();
    initViewModes();
    initFilters();
  });
})();
