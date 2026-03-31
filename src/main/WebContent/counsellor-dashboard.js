// counsellor-dashboard.js

let appointments = [];
let currentView = 'counsellor';
let currentStatusFilter = 'all';
let currentMonthFilter = 'all';
let searchTerm = '';

// DOM elements
const counsellorAvatar  = document.getElementById('counsellorAvatar');
const counsellorName    = document.getElementById('counsellorName');
const counsellorRole    = document.getElementById('counsellorRole');
const topBarAvatar      = document.getElementById('topBarAvatar');
const topBarName        = document.getElementById('topBarName');
const currentDateSpan   = document.getElementById('currentDate');
const totalCount        = document.getElementById('totalCount');
const todayCount        = document.getElementById('todayCount');
const upcomingCount     = document.getElementById('upcomingCount');
const completedCount    = document.getElementById('completedCount');
const alertMessage      = document.getElementById('alertMessage');
const tableTitle        = document.getElementById('tableTitle');
const appointmentsBody  = document.getElementById('appointmentsBody');
const filterButtons     = document.querySelectorAll('.filter-btn');
const searchInput       = document.getElementById('searchInput');
const monthSelect       = document.getElementById('monthSelect');
const resetBtn          = document.getElementById('resetBtn');
const errorMessageDiv   = document.getElementById('errorMessage');
const modal             = document.getElementById('detailModal');
const modalDetails      = document.getElementById('modalDetails');
const closeModalBtn     = document.getElementById('closeModalBtn');
const closeBtn          = document.getElementById('closeBtn');
const logoutBtn         = document.getElementById('logoutBtn');
const logoutModal       = document.getElementById('logoutModal');
const cancelLogoutBtn   = document.getElementById('cancelLogoutBtn');
const confirmLogoutBtn  = document.getElementById('confirmLogoutBtn');
const counsellorTab     = document.getElementById('counsellorTab');
const doctorTab         = document.getElementById('doctorTab');

// ── Initialize ───────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function () {
    setCurrentDate();
    fetchCounsellorProfile();
    fetchAppointments();
    loadMyRatings();

    if (counsellorTab) counsellorTab.addEventListener('click', () => switchView('counsellor'));
    if (doctorTab)     doctorTab.addEventListener('click',     () => switchView('doctor'));

    filterButtons.forEach(btn => btn.addEventListener('click', () => setStatusFilter(btn.dataset.filter)));
    if (searchInput)  searchInput.addEventListener('input',  handleSearch);
    if (monthSelect)  monthSelect.addEventListener('change', handleMonthChange);
    if (resetBtn)     resetBtn.addEventListener('click',     resetFilters);

    if (closeModalBtn) closeModalBtn.addEventListener('click', closeModal);
    if (closeBtn)      closeBtn.addEventListener('click',      closeModal);
    window.addEventListener('click', e => { if (e.target === modal) closeModal(); });

    if (logoutBtn)        logoutBtn.addEventListener('click',        openLogoutModal);
    if (cancelLogoutBtn)  cancelLogoutBtn.addEventListener('click',  closeLogoutModal);
    if (confirmLogoutBtn) confirmLogoutBtn.addEventListener('click', doLogout);
});

function setCurrentDate() {
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
    if (currentDateSpan) currentDateSpan.innerText = new Date().toLocaleDateString('en-US', options);
}

// ── Fetch counsellor profile ─────────────────────────────────────────────────
async function fetchCounsellorProfile() {
    try {
        const res  = await fetch('/OMHCP/CounsellorProfileServlet');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (data.success) {
            if (counsellorAvatar) counsellorAvatar.innerText = data.avatar || 'DR';
            if (counsellorName)   counsellorName.innerText   = data.name   || 'Counsellor';
            if (counsellorRole)   counsellorRole.innerText   = data.role   || 'Counsellor';
            if (topBarAvatar)     topBarAvatar.innerText     = data.avatar || 'DR';
            if (topBarName)       topBarName.innerText       = data.name   || 'Counsellor';
        } else { setDefaultProfile(); }
    } catch (e) { console.error('Profile error:', e); setDefaultProfile(); }
}

function setDefaultProfile() {
    if (counsellorName) counsellorName.innerText = 'Counsellor';
    if (counsellorRole) counsellorRole.innerText = 'Counsellor';
    if (topBarName)     topBarName.innerText     = 'Counsellor';
}

// ── Fetch appointments ───────────────────────────────────────────────────────
async function fetchAppointments() {
    try {
        hideError();
        if (appointmentsBody)
            appointmentsBody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:30px;">Loading appointments...</td></tr>';

        const url = currentView === 'doctor'
            ? '/OMHCP/AppointmentServlet?action=getAll'
            : '/OMHCP/AppointmentServlet';

        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();

        if (data.success) {
            appointments = data.appointments || [];
            applyFilters();
        } else {
            showError(data.message || 'Failed to load appointments');
        }
    } catch (error) {
        console.error('Fetch error:', error);
        showError('Could not load appointments. Please try again.');
    }
}

// ── Switch tabs ──────────────────────────────────────────────────────────────
function switchView(view) {
    currentView = view;
    if (view === 'counsellor') {
        if (counsellorTab) counsellorTab.classList.add('active');
        if (doctorTab)     doctorTab.classList.remove('active');
        if (tableTitle)    tableTitle.innerText   = 'My Appointments';
        if (alertMessage)  alertMessage.innerHTML = '<strong>Showing:</strong> Your own appointments';
    } else {
        if (counsellorTab) counsellorTab.classList.remove('active');
        if (doctorTab)     doctorTab.classList.add('active');
        if (tableTitle)    tableTitle.innerText   = 'All Appointments';
        if (alertMessage)  alertMessage.innerHTML = '<strong>Showing:</strong> All counsellors\' appointments';
    }
    fetchAppointments();
}

// ── Apply filters ────────────────────────────────────────────────────────────
function applyFilters() {
    let filtered = [...appointments];

    if (currentStatusFilter === 'upcoming') {
        filtered = filtered.filter(a => {
            const s = (a.status || '').toLowerCase();
            return s === 'pending' || s === 'confirmed';
        });
    } else if (currentStatusFilter === 'completed') {
        filtered = filtered.filter(a => (a.status || '').toLowerCase() === 'completed');
    } else if (currentStatusFilter === 'cancelled') {
        filtered = filtered.filter(a => (a.status || '').toLowerCase() === 'cancelled');
    }

    if (currentMonthFilter !== 'all') {
        filtered = filtered.filter(a => a.date && a.date.substring(0, 7) === currentMonthFilter);
    }

    if (searchTerm) {
        const term = searchTerm.toLowerCase();
        filtered = filtered.filter(a =>
            (a.patient        || '').toLowerCase().includes(term) ||
            (a.counsellorName || '').toLowerCase().includes(term)
        );
    }

    renderAppointments(filtered);
    updateStats();
}

// ── Render table ─────────────────────────────────────────────────────────────
function renderAppointments(data) {
    if (!data || data.length === 0) {
        if (appointmentsBody)
            appointmentsBody.innerHTML = '<tr><td colspan="9" class="no-data">No appointments found</td></tr>';
        return;
    }

    const tableHeader = document.getElementById('tableHeader');
    if (tableHeader) {
        if (currentView === 'doctor') {
            tableHeader.innerHTML = `
                <th>Patient Name</th><th>Counsellor</th><th>Date</th><th>Time</th>
                <th>Reason</th><th>Status</th><th>Prescription</th><th>Actions</th><th>Details</th>`;
        } else {
            tableHeader.innerHTML = `
                <th>Patient Name</th><th>Date</th><th>Time</th><th>Reason</th>
                <th>Status</th><th>Prescription</th><th>Actions</th><th>Details</th>`;
        }
    }

    let html = '';
    data.forEach(app => {
        const statusClass     = getStatusClass(app.status);
        const patientName     = encodeURIComponent(app.patient || '');
        const patientId       = app.patientId || app.patient_id || '';
        const prescriptionUrl = `prescription-upload.html?patientId=${patientId}&patient=${patientName}&appointmentId=${app.id || ''}`;
        const counsellorCell  = currentView === 'doctor'
            ? `<td>${escapeHtml(app.counsellorName || 'N/A')}</td>` : '';

        const s = (app.status || '').toLowerCase();

        let statusBtns = '';
        if (s === 'pending') {
            statusBtns = `
                <button class="btn-confirm" onclick="changeStatus(${app.id},'confirmed')" title="Confirm Appointment">
                    <i class="fas fa-check-circle"></i> Confirm
                </button>
                <button class="btn-cancel-appt" onclick="changeStatus(${app.id},'cancelled')" title="Cancel">
                    <i class="fas fa-times"></i> Cancel
                </button>`;
        } else if (s === 'confirmed') {
            statusBtns = `
                <button class="btn-complete" onclick="changeStatus(${app.id},'completed')" title="Mark Completed">
                    <i class="fas fa-check"></i> Done
                </button>
                <button class="btn-cancel-appt" onclick="changeStatus(${app.id},'cancelled')" title="Cancel">
                    <i class="fas fa-times"></i> Cancel
                </button>`;
        }

        html += `
        <tr data-id="${app.id || ''}" id="row-${app.id}">
            <td><strong>${escapeHtml(app.patient || 'N/A')}</strong></td>
            ${counsellorCell}
            <td>${escapeHtml(app.date   || 'N/A')}</td>
            <td>${escapeHtml(app.time   || 'N/A')}</td>
            <td>${escapeHtml(app.reason || 'General')}</td>
            <td id="status-${app.id}">
                <span class="status ${statusClass}">${escapeHtml(app.status || 'Unknown')}</span>
                <div class="status-btns">${statusBtns}</div>
            </td>
            <td>
                <button class="btn-upload-presc" onclick="window.location.href='${prescriptionUrl}'">
                    <i class="fas fa-prescription-bottle-alt"></i> Upload
                </button>
            </td>
            <td class="action-icons-cell">
                <i class="fas fa-video"          title="Video call" onclick="startVideoCall(${app.id || 0})"></i>
                <i class="fas fa-phone-alt"       title="Audio call" onclick="startAudioCall(${app.id || 0})"></i>
                <i class="fas fa-comment-medical" title="Chat"       onclick="startChat(${app.id || 0})"></i>
            </td>
            <td>
                <button class="view-btn" onclick='showDetails(${JSON.stringify(app).replace(/'/g, "\\'")})'>
                    <i class="fas fa-eye"></i> Open
                </button>
            </td>
        </tr>`;
    });

    if (appointmentsBody) appointmentsBody.innerHTML = html;
}

function getStatusClass(status) {
    if (!status) return '';
    const s = status.toLowerCase().trim();
    if (s === 'pending')                       return 'pending';
    if (s === 'confirmed')                     return 'confirmed';
    if (s === 'completed')                     return 'completed';
    if (s === 'cancelled' || s === 'canceled') return 'cancelled';
    return 'pending';
}

function updateStats() {
    if (totalCount) totalCount.innerText = appointments.length;
    const today = new Date().toISOString().split('T')[0];
    if (todayCount)    todayCount.innerText    = appointments.filter(a => a.date === today).length;
    if (upcomingCount) upcomingCount.innerText = appointments.filter(a => {
        const s = (a.status || '').toLowerCase();
        return s === 'pending' || s === 'confirmed';
    }).length;
    if (completedCount) completedCount.innerText = appointments.filter(a =>
        (a.status || '').toLowerCase() === 'completed').length;
}

// ── Filter controls ───────────────────────────────────────────────────────────
function setStatusFilter(filter) {
    currentStatusFilter = filter;
    filterButtons.forEach(btn => btn.classList.toggle('active', btn.dataset.filter === filter));
    applyFilters();
}
function handleSearch()      { searchTerm = searchInput.value.trim(); applyFilters(); }
function handleMonthChange() { currentMonthFilter = monthSelect.value; applyFilters(); }
function resetFilters() {
    currentStatusFilter = 'all';
    filterButtons.forEach(btn => btn.classList.toggle('active', btn.dataset.filter === 'all'));
    if (searchInput) searchInput.value = '';
    searchTerm = '';
    if (monthSelect) monthSelect.value = 'all';
    currentMonthFilter = 'all';
    applyFilters();
}

// ── Appointment detail modal ──────────────────────────────────────────────────
function showDetails(app) {
    if (!modalDetails) return;
    const counsellorRow = app.counsellorName
        ? `<div class="detail-row"><span class="detail-label">Counsellor:</span> ${escapeHtml(app.counsellorName)}</div>` : '';
    modalDetails.innerHTML = `
        <div class="detail-row"><span class="detail-label">Patient:</span>  ${escapeHtml(app.patient  || 'N/A')}</div>
        ${counsellorRow}
        <div class="detail-row"><span class="detail-label">Date:</span>     ${escapeHtml(app.date     || 'N/A')}</div>
        <div class="detail-row"><span class="detail-label">Time:</span>     ${escapeHtml(app.time     || 'N/A')}</div>
        <div class="detail-row"><span class="detail-label">Reason:</span>   ${escapeHtml(app.reason   || 'General')}</div>
        <div class="detail-row"><span class="detail-label">Status:</span>   ${escapeHtml(app.status   || 'N/A')}</div>`;
    if (modal) modal.classList.add('active');
}
function closeModal() { if (modal) modal.classList.remove('active'); }

// ── Logout ────────────────────────────────────────────────────────────────────
function openLogoutModal()  { if (logoutModal) logoutModal.classList.add('active'); }
function closeLogoutModal() { if (logoutModal) logoutModal.classList.remove('active'); }
function doLogout() {
    fetch('/OMHCP/LogoutServlet', { method: 'POST' })
        .then(() => { window.location.href = 'login.html'; })
        .catch(() => { window.location.href = 'login.html'; });
}

// ════════════════════════════════════════════════════
//  📹 VIDEO & AUDIO CALLS  (WebRTC)
// ════════════════════════════════════════════════════

async function startVideoCall(appointmentId) {
    const appt = appointments.find(a => a.id === appointmentId);
    const patientName   = appt ? (appt.patient   || 'Patient') : 'Patient';
    const patientUserId = appt ? (appt.patientId  || appt.patient_id || 0) : 0;

    showToast('📞 Starting video call…', 'success');

    let callId = '';
    try {
        const res  = await fetch('/OMHCP/CallServlet', {
            method : 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body   : `action=initiateCall&targetUserId=${patientUserId}&callType=video`
        });
        const data = await res.json();
        if (data.success) {
            callId = data.callId;
        } else {
            showToast(data.message || 'Could not initiate call', 'error');
            return;
        }
    } catch (e) {
        showToast('Network error starting call', 'error');
        return;
    }

    const url = `video-call.html`
              + `?appointmentId=${appointmentId}`
              + `&patientName=${encodeURIComponent(patientName)}`
              + `&callId=${encodeURIComponent(callId)}`
              + `&caller=true`;

    window.open(url, '_blank', 'width=1100,height=700,menubar=no,toolbar=no,location=no,status=no');
}

async function startAudioCall(appointmentId) {
    const appt = appointments.find(a => a.id === appointmentId);
    const patientName   = appt ? (appt.patient   || 'Patient') : 'Patient';
    const patientUserId = appt ? (appt.patientId  || appt.patient_id || 0) : 0;

    showToast('📞 Starting audio call…', 'success');

    let callId = '';
    try {
        const res  = await fetch('/OMHCP/CallServlet', {
            method : 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body   : `action=initiateCall&targetUserId=${patientUserId}&callType=audio`
        });
        const data = await res.json();
        if (data.success) {
            callId = data.callId;
        } else {
            showToast(data.message || 'Could not initiate call', 'error');
            return;
        }
    } catch (e) {
        showToast('Network error starting call', 'error');
        return;
    }

    const url = `video-call.html`
              + `?appointmentId=${appointmentId}`
              + `&patientName=${encodeURIComponent(patientName)}`
              + `&callId=${encodeURIComponent(callId)}`
              + `&caller=true`
              + `&audioOnly=true`;

    window.open(url, '_blank', 'width=600,height=420,menubar=no,toolbar=no,location=no,status=no');
}

function startChat(id) {
    window.location.href = 'messages.html?appointmentId=' + id;
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
function showError(message) {
    if (errorMessageDiv) { errorMessageDiv.style.display = 'block'; errorMessageDiv.innerText = message; }
    if (appointmentsBody) appointmentsBody.innerHTML = `<tr><td colspan="9" class="error">${escapeHtml(message)}</td></tr>`;
}
function hideError() { if (errorMessageDiv) errorMessageDiv.style.display = 'none'; }

// Auto-refresh every 30 seconds
setInterval(() => { if (document.visibilityState === 'visible') fetchAppointments(); }, 30000);


// ════════════════════════════════════════════════════
//  ⭐ RATINGS
// ════════════════════════════════════════════════════
async function loadMyRatings() {
    try {
        const res  = await fetch('/OMHCP/CounsellorRatingsServlet?action=getMyRatings');
        const data = await res.json();
        if (!data.success) { showNoRatings(); return; }
        renderRatingSummary(data.summary);
        renderReviews(data.reviews);
    } catch (e) { console.error('Rating error:', e); showNoRatings(); }
}

function renderRatingSummary(s) {
    if (!s || s.totalReviews === 0) { showNoRatings(); return; }
    const avg  = parseFloat(s.avgOverall || 0);
    document.getElementById('scoreNum').textContent = avg.toFixed(1);
    const full = Math.floor(avg), half = (avg - full) >= 0.5;
    document.getElementById('bigStars').textContent =
        '★'.repeat(full) + (half ? '½' : '') + '☆'.repeat(5 - full - (half ? 1 : 0));
    document.getElementById('reviewCount').textContent =
        s.totalReviews + ' patient review' + (s.totalReviews !== 1 ? 's' : '');
    if (s.wouldRecommend > 0) {
        const pct = Math.round(s.wouldRecommend / s.totalReviews * 100);
        document.getElementById('recBadge').style.display = 'inline-flex';
        document.getElementById('recText').textContent    = pct + '% recommend you';
    }
    const total = s.totalReviews || 1;
    [5, 4, 3, 2, 1].forEach(n => {
        const cnt = s['star' + n] || 0;
        document.getElementById('bar' + n).style.width = Math.round(cnt / total * 100) + '%';
        document.getElementById('cnt' + n).textContent  = cnt;
    });
    setSubRating('Comm', s.avgCommunication);
    setSubRating('Emp',  s.avgEmpathy);
    setSubRating('Help', s.avgHelpfulness);
}
function setSubRating(key, val) {
    const v = parseFloat(val || 0);
    document.getElementById('val' + key).textContent = v > 0 ? v.toFixed(1) : '—';
    document.getElementById('bar' + key).style.width = (v / 5 * 100) + '%';
}
function renderReviews(reviews) {
    const list = document.getElementById('reviewsList');
    if (!reviews || !reviews.length) {
        list.innerHTML = `<div class="rv-empty"><div class="rv-empty-icon">💬</div><p>No patient reviews yet.</p></div>`;
        return;
    }
    list.innerHTML = reviews.map(r => `
        <div class="rv-item">
            <div class="rv-top">
                <div>
                    <div class="rv-name"><i class="fas fa-user-circle" style="color:#b0c4d8;margin-right:5px"></i>${escapeHtml(r.patientName)}</div>
                    <div class="rv-date">${fmtDate(r.submittedAt)}</div>
                </div>
                <div class="rv-stars">${'★'.repeat(r.ratingOverall)}${'☆'.repeat(5 - r.ratingOverall)}</div>
            </div>
            ${r.tags ? `<div class="rv-tags">${r.tags.split(',').map(t => `<span class="rv-tag">${escapeHtml(t.trim())}</span>`).join('')}</div>` : ''}
            ${r.comment ? `<div class="rv-comment">${escapeHtml(r.comment)}</div>` : ''}
            ${r.recommend ? `<span class="rv-rec ${r.recommend === 'true' ? 'yes' : 'no'}">
                <i class="fas fa-thumbs-${r.recommend === 'true' ? 'up' : 'down'}"></i>
                ${r.recommend === 'true' ? 'Recommends you' : 'Does not recommend'}
            </span>` : ''}
        </div>`).join('');
}
function showNoRatings() {
    const el = document.getElementById('scoreNum');  if (el) el.textContent = '—';
    const bs = document.getElementById('bigStars');  if (bs) bs.textContent = '☆☆☆☆☆';
    const rc = document.getElementById('reviewCount'); if (rc) rc.textContent = 'No reviews yet';
    const list = document.getElementById('reviewsList');
    if (list) list.innerHTML = `<div class="rv-empty"><div class="rv-empty-icon">⭐</div><p>No reviews yet!</p></div>`;
}
function fmtDate(s) {
    if (!s) return '—';
    try { return new Date(s).toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' }); }
    catch { return s; }
}


// ════════════════════════════════════════════════════
//  ✅ STATUS CHANGE  —  BUG FIX APPLIED HERE
//  Previously: view=${currentView} sent 'doctor'
//  but Java checks for 'all' → always failed.
//  Fix: map 'doctor' → 'all', 'counsellor' stays as-is.
// ════════════════════════════════════════════════════
function changeStatus(appointmentId, newStatus) {
    const messages = {
        confirmed : '✅ Confirm this appointment? Patient will be notified.',
        completed : 'Mark this appointment as completed?',
        cancelled : '❌ Cancel this appointment? Patient will be notified.'
    };
    if (!confirm(messages[newStatus] || `Change status to ${newStatus}?`)) return;

    const statusCell = document.getElementById('status-' + appointmentId);
    if (statusCell)
        statusCell.innerHTML = '<span style="color:#9ca3af;font-size:12px"><i class="fas fa-spinner fa-spin"></i> Updating…</span>';

    // ✅ FIX: 'doctor' tab must send 'all' so Java's isAllView check passes correctly
    const viewParam = currentView === 'doctor' ? 'all' : 'counsellor';

    fetch('/OMHCP/AppointmentServlet', {
        method : 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body   : `action=updateStatus&appointmentId=${appointmentId}&status=${newStatus}&view=${viewParam}`
    })
    .then(r => r.json())
    .then(d => {
        if (d.success) {
            const cssClass = getStatusClass(newStatus);
            let nextBtns = '';
            if (newStatus === 'confirmed') {
                nextBtns = `
                    <button class="btn-complete" onclick="changeStatus(${appointmentId},'completed')">
                        <i class="fas fa-check"></i> Done
                    </button>
                    <button class="btn-cancel-appt" onclick="changeStatus(${appointmentId},'cancelled')">
                        <i class="fas fa-times"></i> Cancel
                    </button>`;
            }
            if (statusCell) {
                statusCell.innerHTML = `
                    <span class="status ${cssClass}">${newStatus}</span>
                    <div class="status-btns">${nextBtns}</div>`;
            }
            const appt = appointments.find(a => a.id === appointmentId);
            if (appt) appt.status = newStatus;
            updateStats();

            const toastMsg = {
                confirmed : '✅ Appointment confirmed! Patient has been notified.',
                completed : '✅ Appointment marked as completed!',
                cancelled : '❌ Appointment cancelled. Patient has been notified.'
            };
            showToast(toastMsg[newStatus] || 'Status updated!', newStatus === 'cancelled' ? 'error' : 'success');
        } else {
            showToast(d.message || 'Update failed', 'error');
            fetchAppointments();
        }
    })
    .catch(() => {
        showToast('Network error. Please try again.', 'error');
        fetchAppointments();
    });
}

// ── Toast ─────────────────────────────────────────────────────────────────────
function showToast(msg, type = 'success') {
    const old = document.getElementById('dashToast');
    if (old) old.remove();

    const toast = document.createElement('div');
    toast.id = 'dashToast';
    toast.style.cssText = `
        position:fixed;bottom:24px;right:24px;z-index:9999;
        background:${type === 'success' ? '#10b981' : '#ef4444'};
        color:#fff;padding:13px 20px;border-radius:10px;
        font-size:14px;font-weight:600;
        display:flex;align-items:center;gap:9px;
        box-shadow:0 8px 24px rgba(0,0,0,.2);
        animation:slideUp .3s ease;
    `;
    toast.innerHTML = `<i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-circle'}"></i> ${msg}`;
    document.body.appendChild(toast);

    const style = document.createElement('style');
    style.textContent = `@keyframes slideUp{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}`;
    document.head.appendChild(style);

    setTimeout(() => toast.remove(), 3500);
}