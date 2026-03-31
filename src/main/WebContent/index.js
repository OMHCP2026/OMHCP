// ============================================================
//  EXISTING FUNCTIONALITY
// ============================================================
const scrollTop = document.getElementById('scrollTop');
window.addEventListener('scroll', () => {
    scrollTop.classList.toggle('show', window.pageYOffset > 300);
});
scrollTop.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
});

document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
        e.preventDefault();
        const t = document.querySelector(this.getAttribute('href'));
        if (t) t.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
});

const mobileMenu = document.querySelector('.mobile-menu');
const navLinks   = document.querySelector('.nav-links');
mobileMenu.addEventListener('click', () => {
    navLinks.style.display = navLinks.style.display === 'flex' ? 'none' : 'flex';
});

document.addEventListener('DOMContentLoaded', () => {
    const statsSection = document.querySelector('.stats');
    if (statsSection) {
        const observer = new IntersectionObserver(entries => {
            if (entries[0].isIntersecting) {
                document.querySelectorAll('.stat-card h3').forEach(stat => {
                    stat.dataset.original = stat.textContent;
                    const target = parseInt(stat.textContent);
                    let count = 0;
                    const inc = target / 50;
                    const go = () => {
                        if (count < target) {
                            count += inc;
                            stat.textContent = Math.ceil(count)
                                + (stat.dataset.original.includes('+') ? '+' : '')
                                + (stat.dataset.original.includes('/') ? '/5' : '');
                            requestAnimationFrame(go);
                        } else { stat.textContent = stat.dataset.original; }
                    };
                    go();
                });
                observer.unobserve(statsSection);
            }
        });
        observer.observe(statsSection);
    }
});

// ============================================================
//  PATIENT REVIEWS — REAL DATA ONLY from FeedbackServlet
// ============================================================
const SERVLET_URL = 'FeedbackServlet';
const PAGE_SIZE   = 6;
const GRADIENTS   = [
    'linear-gradient(135deg,#2980b9,#1a5276)',
    'linear-gradient(135deg,#27ae60,#1e8449)',
    'linear-gradient(135deg,#8e44ad,#6c3483)',
    'linear-gradient(135deg,#e67e22,#d35400)',
    'linear-gradient(135deg,#c0392b,#922b21)',
    'linear-gradient(135deg,#16a085,#0e6655)',
    'linear-gradient(135deg,#2471a3,#154360)',
];

let allReviews    = [];
let displayed     = 0;
let currentFilter = 'all';
let currentSort   = 'newest';
let helpedSet     = new Set();

function timeAgo(dateStr) {
    const date = new Date(dateStr);
    const diff = Math.floor((Date.now() - date) / 1000);
    if (isNaN(diff) || diff < 0) return 'Recently';
    if (diff < 60)     return 'Just now';
    if (diff < 3600)   return Math.floor(diff / 60)    + 'm ago';
    if (diff < 86400)  return Math.floor(diff / 3600)  + 'h ago';
    if (diff < 604800) return Math.floor(diff / 86400) + 'd ago';
    return date.toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' });
}

function starsHtml(n) {
    let h = '';
    for (let i = 1; i <= 5; i++)
        h += `<i class="fa${i <= n ? 's' : 'r'} fa-star"></i>`;
    return h;
}

function gradientFor(name) {
    return GRADIENTS[(name && name.charCodeAt(0) || 65) % GRADIENTS.length];
}

function normTags(r) {
    return {
        ...r,
        tags: Array.isArray(r.tags) ? r.tags
            : (r.tags ? String(r.tags).split(',').map(t => t.trim()).filter(Boolean) : [])
    };
}

function applyStats(s) {
    const a = document.getElementById('liveAvgScore');
    const t = document.getElementById('liveTotalReviews');
    const w = document.getElementById('liveWeekCount');
    const starsDiv = document.getElementById('liveAvgStars');
    if (a) a.textContent = Number(s.avgRating || 0).toFixed(1);
    if (t) t.textContent = Number(s.total || 0).toLocaleString('en-IN');
    if (w) w.textContent = (s.thisWeek ? '+' + s.thisWeek : '--');
    if (starsDiv) {
        const avg = Math.round(s.avgRating || 0);
        starsDiv.innerHTML = Array(5).fill(0).map((_, i) => 
            `<i class="fa${i < avg ? 's' : 'r'} fa-star"></i>`
        ).join('');
    }

    const pcts = [s.pct5, s.pct4, s.pct3, s.pct2, s.pct1];
    document.querySelectorAll('.bar-fill').forEach((bar, i) => {
        if (pcts[i] != null) bar.dataset.width = pcts[i];
    });
    document.querySelectorAll('.bar-pct').forEach((el, i) => {
        if (pcts[i] != null) el.textContent = pcts[i] + '%';
    });
    animateBars();
}

function animateBars() {
    document.querySelectorAll('.bar-fill').forEach(bar => {
        if (bar.dataset.width != null) bar.style.width = bar.dataset.width + '%';
    });
}

function buildCard(r, delay) {
    const el = document.createElement('div');
    el.className = 'review-card';
    el.dataset.id = r.id;
    el.dataset.rating = r.rating;
    el.style.animationDelay = delay + 'ms';

    const tags     = Array.isArray(r.tags) ? r.tags : [];
    const tagChips = tags.map(t => `<span class="rev-tag-chip">${t}</span>`).join('');
    const rec      = r.recommend === 'yes';
    const grad     = gradientFor(r.name);
    const init     = r.name ? r.name.charAt(0).toUpperCase() : '?';

    el.innerHTML = `
        <div class="rev-card-top">
            <div class="rev-avatar" style="background:${grad}">${init}</div>
            <div>
                <div class="rev-user-name">${r.name}</div>
                <div class="rev-user-role">
                    <i class="fas fa-user" style="font-size:0.7rem;margin-right:4px;color:#a9cce3"></i>
                    Verified Patient
                </div>
            </div>
        </div>
        <div class="rev-stars">${starsHtml(r.rating)}</div>
        <span class="rev-counsellor-tag">
            <i class="fas fa-user-md" style="font-size:0.7rem"></i> ${r.counsellorName}
        </span>
        ${rec ? '<span class="rev-recommend-badge"><i class="fas fa-thumbs-up"></i> Recommends</span>' : ''}
        <div class="rev-comment">${r.comment}</div>
        ${tagChips ? `<div class="rev-tags-row">${tagChips}</div>` : ''}
        <div class="rev-card-footer">
            <span class="rev-date"><i class="fas fa-clock"></i> ${timeAgo(r.createdAt)}</span>
            <button class="rev-helpful-btn${helpedSet.has(r.id) ? ' liked' : ''}"
                    onclick="toggleHelpful(${r.id},this)">
                <i class="fas fa-thumbs-up"></i> Helpful
            </button>
        </div>`;
    return el;
}

function getFiltered() {
    let list = [...allReviews];
    if (currentFilter === '5')      list = list.filter(r => r.rating === 5);
    else if (currentFilter === '4') list = list.filter(r => r.rating === 4);
    else if (currentFilter === 'recent') list = list.slice(0, 10);
    if (currentSort === 'highest')  list.sort((a, b) => b.rating - a.rating);
    else list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    return list;
}

function renderGrid() {
    const grid = document.getElementById('reviewsGrid');
    if (!grid) return;
    grid.innerHTML = '';
    displayed = 0;
    const list = getFiltered().slice(0, PAGE_SIZE);
    displayed  = list.length;

    // No reviews → do nothing (grid stays empty)
    if (!list.length) {
        return;
    }
    list.forEach((r, i) => grid.appendChild(buildCard(r, i * 60)));
}

function filterRevCards(f, btn) {
    currentFilter = f;
    document.querySelectorAll('.rev-filter-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    renderGrid();
}

function sortRevCards(v) {
    currentSort = v;
    renderGrid();
}

function loadMoreRevCards() {
    const grid = document.getElementById('reviewsGrid');
    if (!grid) return;
    const next = getFiltered().slice(displayed, displayed + PAGE_SIZE);
    if (!next.length) { showToast('All reviews loaded!'); return; }
    next.forEach((r, i) => grid.appendChild(buildCard(r, i * 60)));
    displayed += next.length;
}

function toggleHelpful(id, btn) {
    if (helpedSet.has(id)) { helpedSet.delete(id); btn.classList.remove('liked'); }
    else                   { helpedSet.add(id);    btn.classList.add('liked');    }
}

function showToast(msg) {
    const t = document.getElementById('liveReviewToast');
    const x = document.getElementById('liveToastText');
    if (!t || !x) return;
    x.textContent = msg;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 4000);
}

function showSkeleton() {
    const grid = document.getElementById('reviewsGrid');
    if (!grid) return;
    grid.innerHTML = Array.from({length: 6}, () => `
        <div class="review-card" style="min-height:180px;">
            <div style="background:rgba(255,255,255,0.22);border-radius:8px;height:14px;width:55%;margin-bottom:12px"></div>
            <div style="background:rgba(255,255,255,0.16);border-radius:8px;height:10px;width:75%;margin-bottom:8px"></div>
            <div style="background:rgba(255,255,255,0.12);border-radius:8px;height:10px;width:65%;margin-bottom:8px"></div>
            <div style="background:rgba(255,255,255,0.08);border-radius:8px;height:10px;width:45%"></div>
        </div>`).join('');
}

function showError(msg) {
    const grid = document.getElementById('reviewsGrid');
    if (!grid) return;
    grid.innerHTML = `
        <div style="grid-column:1/-1;text-align:center;padding:3rem;color:rgba(255,255,255,0.85)">
            <i class="fas fa-exclamation-circle" style="font-size:2rem;display:block;margin-bottom:1rem;color:#f39c12"></i>
            ${msg}<br>
            <button onclick="loadReviews()" style="margin-top:1rem;padding:8px 24px;
                background:#fff;color:#1a5276;border:none;border-radius:50px;font-weight:700;cursor:pointer">
                Try Again
            </button>
        </div>`;
}

function loadReviews() {
    showSkeleton();
    fetch(SERVLET_URL + '?action=getAllFeedback&limit=100&_=' + Date.now())
        .then(res => {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        })
        .then(data => {
            if (!data.success) throw new Error(data.message || 'Server error');
            applyStats(data.stats || {});
            allReviews = (data.reviews || []).map(normTags);
            renderGrid();
        })
        .catch(err => {
            console.error('Reviews load failed:', err);
            showError('Could not load reviews. Please refresh.');
        });
}

function startPolling() {
    setInterval(() => {
        fetch(SERVLET_URL + '?action=getAllFeedback&limit=5&_=' + Date.now())
            .then(r => r.json())
            .then(data => {
                if (!data.success || !data.reviews) return;
                const existIds = new Set(allReviews.map(r => r.id));
                const newOnes  = data.reviews.filter(r => !existIds.has(r.id));
                if (!newOnes.length) return;
                applyStats(data.stats);
                allReviews.unshift(...newOnes.map(normTags));
                const nr = newOnes[0];
                showToast(nr.name + ' just reviewed ' + nr.counsellorName + ' — ' + '★'.repeat(nr.rating));
                if (currentFilter === 'all' && currentSort === 'newest') {
                    const grid = document.getElementById('reviewsGrid');
                    if (grid) {
                        const card = buildCard(normTags(nr), 0);
                        card.classList.add('is-new');
                        grid.insertBefore(card, grid.firstChild);
                        if (grid.children.length > PAGE_SIZE + 3) grid.removeChild(grid.lastChild);
                        setTimeout(() => card.classList.remove('is-new'), 8000);
                        displayed++;
                    }
                }
            })
            .catch(() => {});
    }, 30000);
}

document.addEventListener('DOMContentLoaded', () => {
    loadReviews();
    startPolling();

    const revSec = document.getElementById('patient-reviews');
    if (revSec) {
        new IntersectionObserver(entries => {
            if (entries[0].isIntersecting) animateBars();
        }, { threshold: 0.2 }).observe(revSec);
    }

    setInterval(() => {
        document.querySelectorAll('.rev-date').forEach(el => {
            const id  = parseInt(el.closest('.review-card')?.dataset?.id);
            const rev = allReviews.find(r => r.id === id);
            if (rev) el.innerHTML = '<i class="fas fa-clock"></i> ' + timeAgo(rev.createdAt);
        });
    }, 60000);
});