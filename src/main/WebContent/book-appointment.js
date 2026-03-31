// =============================================
//  GLOBAL STATE (unchanged)
// =============================================
let selectedCounselor = null;
let selectedDate      = '';
let selectedTime      = '';
let allCounselors     = [];

const contextPath = (document.getElementById('contextPath')?.value || '').replace(/\/$/, '');

// =============================================
//  INIT (unchanged)
// =============================================
document.addEventListener('DOMContentLoaded', () => {
    setupDateInput();
    setupEventListeners();
    loadCounselors();
});

// =============================================
//  HELPER: clean value (treat null/undefined/"N/A" as empty)
// =============================================
function cleanValue(val) {
    if (val == null) return '';
    if (typeof val === 'string' && val.trim() === 'N/A') return '';
    return val.toString().trim();
}

// =============================================
//  DATE INPUT SETUP (unchanged)
// =============================================
function setupDateInput() {
    const inp = document.getElementById('appointmentDate');
    if (!inp) return;

    const today   = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(today.getDate() + 1);
    const maxDate = new Date(today);
    maxDate.setDate(today.getDate() + 7);

    const fmt = d => d.toISOString().split('T')[0];
    inp.min = fmt(tomorrow);
    inp.max = fmt(maxDate);

    inp.addEventListener('change', () => {
        selectedDate = inp.value;
        selectedTime = '';
        clearSlots();
        validateStep2();
        if (selectedCounselor && selectedDate) loadSlots();
    });
}

// =============================================
//  MISC EVENT LISTENERS (unchanged)
// =============================================
function setupEventListeners() {
    document.getElementById('confirmCheckbox')?.addEventListener('change', e => {
        document.getElementById('confirmBookingBtn').disabled = !e.target.checked;
    });
    document.getElementById('searchCounselor')?.addEventListener('input', filterCounselors);
    document.getElementById('specialtyFilter')?.addEventListener('change', filterCounselors);
}

// =============================================
//  LOAD COUNSELORS (unchanged)
// =============================================
function loadCounselors() {
    setGrid('<div class="loading-wrap"><div class="spinner"></div><span>Loading counselors...</span></div>');

    fetch(`${contextPath}/BookAppointmentServlet?action=getCounselors`, { credentials: 'same-origin' })
        .then(res => {
            const ct = res.headers.get('content-type') || '';
            if (!ct.includes('application/json')) {
                showToast('Session expired or access denied. Redirecting…', 'error');
                setTimeout(() => { window.location.href = contextPath + '/login.html'; }, 2000);
                return null;
            }
            return res.json();
        })
        .then(data => {
            if (!data) return;
            if (data.success) {
                allCounselors = data.counselors || [];
                renderCounselors(allCounselors);
            } else {
                showToast(data.message || 'Could not load counselors.', 'error');
                setGrid(`<p class="no-data">${escHtml(data.message || 'No counselors available.')}</p>`);
                if (data.message && data.message.toLowerCase().includes('login')) {
                    setTimeout(() => { window.location.href = contextPath + '/login.html'; }, 2000);
                }
            }
        })
        .catch(err => {
            console.error(err);
            showToast('Server connection failed.', 'error');
            setGrid('<p class="no-data">Could not reach server.</p>');
        });
}

function setGrid(html) {
    const g = document.getElementById('counselorsList');
    if (g) g.innerHTML = html;
}

// =============================================
//  RENDER COUNSELORS – conditionally show fields
// =============================================
function renderCounselors(list) {
    const g = document.getElementById('counselorsList');
    if (!g) return;
    if (!list || list.length === 0) {
        g.innerHTML = '<p class="no-data">No counselors match your search.</p>';
        return;
    }

    g.innerHTML = list.map(c => {
        const isSelected = selectedCounselor?.id === c.id;
        // Clean each field
        const name      = escHtml(c.name || 'Unknown');
        const specialty = escHtml(c.specialty || 'Specialist');
        const regNo     = cleanValue(c.registration_number);
        const degrees   = cleanValue(c.degrees);
        const college   = cleanValue(c.college);
        const experience = c.experience ? `${c.experience} yrs` : '';
        const languages = cleanValue(c.languages) || 'English'; // default language
        const mode      = cleanValue(c.consultation_mode);
        const rating    = c.rating != null ? c.rating : 0;
        const totalReviews = c.total_reviews != null ? c.total_reviews : 0;
        const reviewsText = totalReviews > 0 ? `${rating} (${totalReviews} reviews)` : '';
        const memberships = cleanValue(c.memberships);
        const certifications = cleanValue(c.certifications);
        const fee       = c.fee ? `₹${c.fee}` : '';

        // Build credentials HTML only for fields with data
        let credentialsHtml = '';

        if (regNo)      credentialsHtml += `<span title="Registration"><i class="fas fa-id-card"></i> ${regNo}</span>`;
        if (degrees)    credentialsHtml += `<span title="Degrees"><i class="fas fa-graduation-cap"></i> ${degrees}</span>`;
        if (college)    credentialsHtml += `<span title="College"><i class="fas fa-university"></i> ${college}</span>`;
        if (experience) credentialsHtml += `<span title="Experience"><i class="fas fa-briefcase"></i> ${experience}</span>`;
        if (languages)  credentialsHtml += `<span title="Languages"><i class="fas fa-language"></i> ${languages}</span>`;
        if (mode)       credentialsHtml += `<span title="Mode"><i class="fas fa-video"></i> ${mode}</span>`;
        if (reviewsText) credentialsHtml += `<span title="Rating"><i class="fas fa-star"></i> ${reviewsText}</span>`;

        // Badges (optional)
        let badgesHtml = '';
        if (memberships)    badgesHtml += `<span class="badge">${memberships}</span>`;
        if (certifications) badgesHtml += `<span class="badge">${certifications}</span>`;

        return `
            <div class="counselor-card ${isSelected ? 'selected' : ''}"
                 id="cc-${c.id}" onclick="selectCounselor(${c.id})">
                <div class="card-header">
                    <img src="${c.profile_pic || 'default-avatar.png'}" alt="${name}" class="profile-thumb">
                    <div class="card-title">${name}</div>
                </div>
                <div class="card-specialty">${specialty}</div>
                <div class="card-credentials">
                    ${credentialsHtml}
                </div>
                ${badgesHtml ? `<div class="card-badges">${badgesHtml}</div>` : ''}
                <div class="card-footer">${fee}</div>
                <div class="selected-tick"><i class="fas fa-check"></i></div>
            </div>
        `;
    }).join('');
}

// =============================================
//  FILTER COUNSELORS (unchanged)
// =============================================
function filterCounselors() {
    const q    = (document.getElementById('searchCounselor')?.value || '').toLowerCase();
    const spec = document.getElementById('specialtyFilter')?.value || 'all';
    const filtered = allCounselors.filter(c =>
        (spec === 'all' || c.specialty === spec) &&
        (c.name.toLowerCase().includes(q) || c.specialty.toLowerCase().includes(q))
    );
    renderCounselors(filtered);
}

// =============================================
//  SELECT COUNSELOR (unchanged)
// =============================================
function selectCounselor(id) {
    selectedCounselor = allCounselors.find(c => c.id === id || c.id === +id);
    if (!selectedCounselor) return;

    document.querySelectorAll('.counselor-card').forEach(el => el.classList.remove('selected'));
    document.getElementById(`cc-${id}`)?.classList.add('selected');

    document.getElementById('nextToStep2').disabled = false;

    selectedDate = '';
    selectedTime = '';
    const di = document.getElementById('appointmentDate');
    if (di) di.value = '';
    clearSlots();
    validateStep2();
}

// =============================================
//  LOAD TIME SLOTS (unchanged)
// =============================================
function loadSlots() {
    document.getElementById('timeSlotsContainer').innerHTML =
        '<div class="loading-wrap"><div class="spinner"></div><span>Loading slots…</span></div>';

    fetch(`${contextPath}/BookAppointmentServlet?action=getSlots&counselorId=${selectedCounselor.id}&date=${selectedDate}`, { credentials: 'same-origin' })
        .then(res => {
            const ct = res.headers.get('content-type') || '';
            if (!ct.includes('application/json')) throw new Error('Session expired. Please login again.');
            return res.json();
        })
        .then(data => {
            if (data.success) renderSlots(data.slots);
            else {
                showToast(data.message || 'No slots available.', 'warning');
                clearSlots();
            }
        })
        .catch(err => {
            showToast(err.message || 'Failed to load slots.', 'error');
            clearSlots();
        });
}

function clearSlots() {
    const c = document.getElementById('timeSlotsContainer');
    if (c) c.innerHTML = '<p class="muted">Select a date to see available slots</p>';
    selectedTime = '';
    validateStep2();
}

// =============================================
//  RENDER SLOTS (unchanged)
// =============================================
function renderSlots(slots) {
    const c = document.getElementById('timeSlotsContainer');
    if (!c) return;
    if (!slots || slots.length === 0) {
        c.innerHTML = '<p class="muted">No slots available for this date.</p>';
        return;
    }
    c.innerHTML = slots.map(s => `
        <div class="slot ${s.available ? '' : 'slot-booked'}"
             ${s.available ? `onclick="pickSlot('${s.time}', this)"` : 'title="Already booked"'}>
            ${s.time}${!s.available ? '<br><small>Booked</small>' : ''}
        </div>
    `).join('');
}

// =============================================
//  PICK SLOT (unchanged)
// =============================================
function pickSlot(time, el) {
    if (!el || el.classList.contains('slot-booked')) return;
    selectedTime = time;
    document.querySelectorAll('.slot').forEach(s => s.classList.remove('slot-selected'));
    el.classList.add('slot-selected');
    validateStep2();
}

function validateStep2() {
    const btn = document.getElementById('nextToStep3');
    if (btn) btn.disabled = !(selectedDate && selectedTime);
}

// =============================================
//  STEP NAVIGATION (unchanged)
// =============================================
function getCurrentStep() {
    for (let i = 1; i <= 3; i++) {
        if (document.getElementById(`step${i}-content`)?.classList.contains('active')) return i;
    }
    return 1;
}

function nextStep() {
    const cur = getCurrentStep();

    if (cur === 1 && !selectedCounselor) { showToast('Please select a counselor first.', 'warning'); return; }
    if (cur === 2 && !selectedDate)       { showToast('Please select a date.', 'warning'); return; }
    if (cur === 2 && !selectedTime)       { showToast('Please select a time slot.', 'warning'); return; }

    goToStep(cur + 1);
}

function prevStep() {
    const cur = getCurrentStep();
    if (cur > 1) goToStep(cur - 1);
}

function goToStep(n) {
    const cur = getCurrentStep();
    document.getElementById(`step${cur}-content`)?.classList.remove('active');
    markStepper(cur, n);
    const next = document.getElementById(`step${n}-content`);
    if (next) next.classList.add('active');

    if (n === 2) populateBadge();
    if (n === 3) populateSummary();
}

function markStepper(from, to) {
    for (let i = 1; i <= 3; i++) {
        const dot  = document.getElementById(`dot${i}`);
        const line = document.getElementById(`line${i}`);
        if (!dot) continue;
        dot.classList.remove('active', 'done');
        if (i < to)  dot.classList.add('done');
        if (i === to) dot.classList.add('active');
        if (line) line.classList.toggle('done', i < to);
    }
}

// =============================================
//  BADGE (step 2) – conditionally show fields
// =============================================
function populateBadge() {
    const b = document.getElementById('counselorBadge');
    if (!b || !selectedCounselor) return;
    const c = selectedCounselor;

    const regNo     = cleanValue(c.registration_number);
    const degrees   = cleanValue(c.degrees);
    const college   = cleanValue(c.college);
    const experience = c.experience ? `${c.experience} years experience` : '';
    const languages = cleanValue(c.languages) || 'English';
    const mode      = cleanValue(c.consultation_mode);
    const rating    = c.rating != null ? c.rating : 0;
    const totalReviews = c.total_reviews != null ? c.total_reviews : 0;
    const reviewsText = totalReviews > 0 ? `${rating} (${totalReviews} reviews)` : '';
    const memberships = cleanValue(c.memberships);
    const certifications = cleanValue(c.certifications);
    const about      = cleanValue(c.about);
    const fee        = c.fee ? `₹${c.fee}` : '';

    // Build lines only for fields with data
    let linesHtml = `<div style="font-size:1.5rem; font-weight:600;">${escHtml(c.name)}</div>`;
    linesHtml += `<div class="doctor-detail-line"><i class="fas fa-stethoscope"></i> ${escHtml(c.specialty)}</div>`;

    if (regNo)  linesHtml += `<div class="doctor-detail-line"><i class="fas fa-id-card"></i> Reg: ${regNo}</div>`;
    if (degrees) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-graduation-cap"></i> ${degrees}</div>`;
    if (college) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-university"></i> ${college}</div>`;
    if (experience) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-briefcase"></i> ${experience}</div>`;
    if (languages) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-language"></i> Languages: ${languages}</div>`;
    if (mode) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-video"></i> Mode: ${mode}</div>`;
    if (reviewsText) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-star"></i> ${reviewsText}</div>`;
    if (certifications) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-certificate"></i> ${certifications}</div>`;
    if (memberships) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-users"></i> ${memberships}</div>`;
    if (fee) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-tag"></i> Fee: ${fee}</div>`;
    if (about) linesHtml += `<div class="doctor-detail-line"><i class="fas fa-heart"></i> ${about}</div>`;

    b.innerHTML = `<div style="display:flex; flex-direction:column; gap:6px;">${linesHtml}</div>`;
}

// =============================================
//  SUMMARY (step 3) – conditionally show fields
// =============================================
function populateSummary() {
    const s = document.getElementById('summaryBox');
    if (!s || !selectedCounselor) return;
    const c = selectedCounselor;

    const regNo     = cleanValue(c.registration_number);
    const degrees   = cleanValue(c.degrees);
    const college   = cleanValue(c.college);
    const experience = c.experience ? `${c.experience} years` : '';
    const languages = cleanValue(c.languages) || 'English';
    const mode      = cleanValue(c.consultation_mode);
    const rating    = c.rating != null ? c.rating : 0;
    const totalReviews = c.total_reviews != null ? c.total_reviews : 0;
    const reviewsText = totalReviews > 0 ? `${rating} (${totalReviews} reviews)` : '';
    const memberships = cleanValue(c.memberships);
    const certifications = cleanValue(c.certifications);
    const about      = cleanValue(c.about);
    const fee        = c.fee ? `₹${c.fee}` : '';

    let detailsHtml = `<h3 style="margin-bottom:10px;">${escHtml(c.name)}</h3>`;
    detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-stethoscope"></i> ${escHtml(c.specialty)}</div>`;

    if (regNo)  detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-id-card"></i> Reg: ${regNo}</div>`;
    if (degrees) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-graduation-cap"></i> ${degrees}</div>`;
    if (college) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-university"></i> ${college}</div>`;
    if (experience) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-briefcase"></i> ${experience}</div>`;
    if (languages) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-language"></i> ${languages}</div>`;
    if (mode) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-video"></i> ${mode}</div>`;
    if (reviewsText) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-star"></i> ${reviewsText}</div>`;
    if (certifications) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-certificate"></i> ${certifications}</div>`;
    if (memberships) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-users"></i> ${memberships}</div>`;
    if (fee) detailsHtml += `<div class="doctor-detail-line"><i class="fas fa-tag"></i> ${fee}</div>`;
    if (about) detailsHtml += `<div style="margin-top:16px; background:#d9ccf0; padding:12px; border-radius:24px;">📝 ${about}</div>`;

    detailsHtml += `<hr style="margin:20px 0; border:1px dashed #b4a0d4;">`;
    detailsHtml += `<p><i class="fas fa-calendar-check"></i> <span id="summaryDate">${selectedDate || 'Not selected'}</span><br>`;
    detailsHtml += `<i class="fas fa-clock"></i> <span id="summaryTime">${selectedTime || '—'}</span></p>`;

    s.innerHTML = detailsHtml;

    const cb  = document.getElementById('confirmCheckbox');
    const btn = document.getElementById('confirmBookingBtn');
    if (cb)  cb.checked = false;
    if (btn) btn.disabled = true;
}

// =============================================
//  BOOK APPOINTMENT (unchanged)
// =============================================
function bookAppointment() {
    if (!selectedCounselor) { showToast('No counselor selected.', 'error'); return; }
    if (!selectedDate)      { showToast('No date selected.', 'error'); return; }
    if (!selectedTime)      { showToast('No time selected.', 'error'); return; }

    const reason = document.getElementById('appointmentReason')?.value.trim();
    if (!reason)            { showToast('Please enter a reason for your visit.', 'error'); return; }

    const btn = document.getElementById('confirmBookingBtn');
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Booking…'; }

    const body = new URLSearchParams({
        action:        'bookAppointment',
        counselorId:   selectedCounselor.id,
        date:          selectedDate,
        time:          selectedTime,
        reason:        reason,
        paymentMethod: document.querySelector('input[name="paymentMethod"]:checked')?.value || 'offline'
    });

    fetch(`${contextPath}/BookAppointmentServlet`, {
        method:      'POST',
        credentials: 'same-origin',
        headers:     { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
    })
    .then(res => {
        const ct = res.headers.get('content-type') || '';
        if (!ct.includes('application/json')) throw new Error('Session expired. Please login again.');
        return res.json();
    })
    .then(data => {
        if (data.success) {
            showToast('Appointment booked successfully! 🎉', 'success');
            setTimeout(() => { window.location.href = contextPath + '/patient-dashboard.html'; }, 2200);
        } else {
            showToast(data.message || 'Booking failed. Please try again.', 'error');
            if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-calendar-check"></i> Confirm Booking'; }
        }
    })
    .catch(err => {
        console.error(err);
        showToast(err.message || 'Something went wrong.', 'error');
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="fas fa-calendar-check"></i> Confirm Booking'; }
    });
}

// =============================================
//  TOAST & HELPERS (unchanged, added cleanValue earlier)
// =============================================
function showToast(msg, type = 'info') {
    const icons = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };
    const c = document.getElementById('toastContainer');
    if (!c) { alert(msg); return; }

    const t = document.createElement('div');
    t.className = `toast ${type}`;
    t.innerHTML = `<span>${icons[type] || ''}</span> ${escHtml(msg)}`;
    c.appendChild(t);
    setTimeout(() => t.remove(), 4000);
}

function escHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// =============================================
//  GLOBAL EXPOSE (unchanged)
// =============================================
window.nextStep        = nextStep;
window.prevStep        = prevStep;
window.selectCounselor = selectCounselor;
window.pickSlot        = pickSlot;
window.bookAppointment = bookAppointment;