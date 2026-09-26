// tabs.js — hash-based tab switching
function switchTab(name) {
    document.querySelectorAll('.tab-btn[data-tab]').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === name);
    });
    document.querySelectorAll('.tab-content').forEach(panel => {
        panel.style.display = panel.id === 'tab-' + name ? 'block' : 'none';
    });
    location.hash = name;
}

// Restore tab from URL hash on load
(function () {
    const hash = location.hash.replace('#', '');
    if (hash === 'create' || hash === 'search' || hash === 'tests') switchTab(hash);
})();
