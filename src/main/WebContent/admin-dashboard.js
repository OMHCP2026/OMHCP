// ═══════════════════════════════════════════════════════════════
//  admin-dashboard.js  —  OMHCP Admin Panel
// ═══════════════════════════════════════════════════════════════
'use strict';

// ── State ─────────────────────────────────────────────────────
let currentPage    = 'dashboard';
let sidebarOpen    = true;
let deleteCallback = null;

// ═══════════════════════════════════════════════════════════════
//  INIT
// ═══════════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
    setHeroDate();
    initNavigation();
    loadDashboard();
    loadBadges();
    setInterval(loadBadges, 60000);

    // ── Add User Form ─────────────────────────────────────────
    const addUserForm = document.getElementById('addUserForm');
    if (addUserForm) {
        addUserForm.addEventListener('submit', async e => {
            e.preventDefault();
            const role = document.getElementById('newRole')?.value;
            const payload = new URLSearchParams({
                action  : 'add',
                name    : document.getElementById('newName')?.value     || '',
                email   : document.getElementById('newEmail')?.value    || '',
                phone   : document.getElementById('newPhone')?.value    || '',
                password: document.getElementById('newPassword')?.value || '',
                role    : role || 'patient'
            });
            if (role === 'counsellor') {
                payload.append('specialty', document.getElementById('newSpecialty')?.value || '');
                payload.append('fee',       document.getElementById('newFee')?.value       || '0');
            }
            try {
                const res  = await fetch('UserServlet', { method: 'POST', body: payload });
                const data = await res.json();
                if (data.success) {
                    closeModal('addUserModal');
                    showToast('✅ User added successfully!', 'success');
                    loadUsers();
                    loadBadges();
                    addUserForm.reset();
                    document.getElementById('counsellorExtraFields').style.display = 'none';
                } else {
                    showToast(data.message || 'Failed to add user.', 'error');
                }
            } catch (e) {
                showToast('Network error. Please try again.', 'error');
            }
        });
    }

    // Show/hide counsellor extra fields
    const newRole = document.getElementById('newRole');
    if (newRole) {
        newRole.addEventListener('change', () => {
            const extra = document.getElementById('counsellorExtraFields');
            if (extra) extra.style.display = newRole.value === 'counsellor' ? 'block' : 'none';
        });
    }

    // ── Edit User Form ────────────────────────────────────────
    const editUserForm = document.getElementById('editUserForm');
    if (editUserForm) {
        editUserForm.addEventListener('submit', async e => {
            e.preventDefault();
            const payload = new URLSearchParams({
                action: 'update',
                id    : document.getElementById('editUserId')?.value || '',
                name  : document.getElementById('editName')?.value   || '',
                role  : document.getElementById('editRole')?.value   || '',
                phone : document.getElementById('editPhone')?.value  || ''
            });
            try {
                const res  = await fetch('UserServlet', { method: 'POST', body: payload });
                const data = await res.json();
                if (data.success) {
                    closeModal('editUserModal');
                    showToast('✅ User updated successfully!', 'success');
                    loadUsers();
                } else {
                    showToast(data.message || 'Update failed.', 'error');
                }
            } catch (e) {
                showToast('Network error. Please try again.', 'error');
            }
        });
    }

    // ── Approve Counsellor Form ───────────────────────────────
    const approveForm = document.getElementById('approveCounsellorForm');
    if (approveForm) {
        approveForm.addEventListener('submit', async e => {
            e.preventDefault();
            const id         = document.getElementById('approveCounsellorId').value;
            const specialty  = document.getElementById('approveSpecialty').value.trim();
            const experience = document.getElementById('approveExperience').value.trim();
            const fee        = document.getElementById('approveFee').value.trim();

            if (!specialty || !experience || !fee) {
                showToast('Please fill all fields before approving.', 'error');
                return;
            }
            try {
                const res  = await fetch('CounsellorServlet', {
                    method: 'POST',
                    body  : new URLSearchParams({
                        action         : 'approveWithDetails',
                        id             : id,
                        specialty      : specialty,
                        experienceYears: experience,
                        fee            : fee
                    })
                });
                const data = await res.json();
                if (data.success) {
                    closeModal('approveCounsellorModal');
                    showToast('✅ Counsellor approved successfully!', 'success');
                    loadCounsellors();
                    loadBadges();
                } else {
                    showToast(data.message || 'Approval failed.', 'error');
                }
            } catch (e) {
                showToast('Network error. Please try again.', 'error');
            }
        });
    }
});

// ═══════════════════════════════════════════════════════════════
//  DATE
// ═══════════════════════════════════════════════════════════════
function setHeroDate() {
    const el = document.getElementById('heroDate');
    if (!el) return;
    const now = new Date();
    el.innerHTML = `
        <div style="text-align:right">
            <div style="font-size:28px;font-weight:700;color:var(--text)">${now.getDate()}</div>
            <div style="font-size:13px;color:var(--muted)">${now.toLocaleDateString('en-IN',{month:'long',year:'numeric'})}</div>
            <div style="font-size:12px;color:var(--muted)">${now.toLocaleDateString('en-IN',{weekday:'long'})}</div>
        </div>`;
}

// ═══════════════════════════════════════════════════════════════
//  SIDEBAR
// ═══════════════════════════════════════════════════════════════
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const main    = document.getElementById('main');
    if (!sidebar || !main) return;
    sidebarOpen = !sidebarOpen;
    sidebar.classList.toggle('collapsed', !sidebarOpen);
    main.classList.toggle('expanded',     !sidebarOpen);
}

// ═══════════════════════════════════════════════════════════════
//  NAVIGATION
// ═══════════════════════════════════════════════════════════════
function initNavigation() {
    document.querySelectorAll('.nav-link[data-page]').forEach(link => {
        link.addEventListener('click', e => {
            e.preventDefault();
            navigateTo(link.dataset.page);
        });
    });
}

function navigateTo(page) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));

    const pageEl = document.getElementById('page-' + page);
    if (pageEl) pageEl.classList.add('active');

    const navEl = document.querySelector(`.nav-link[data-page="${page}"]`);
    if (navEl) navEl.classList.add('active');

    const titles = {
        dashboard   : 'Dashboard',
        users       : 'Patients',
        counsellors : 'Counsellors',
        appointments: 'Appointments',
        analytics   : 'Analytics',
        feedback    : 'Feedback'
    };
    const titleEl = document.getElementById('pageTitle');
    if (titleEl) titleEl.textContent = titles[page] || page;

    currentPage = page;

    switch (page) {
        case 'dashboard':    loadDashboard();    break;
        case 'users':        loadUsers();        break;
        case 'counsellors':  loadCounsellors();  break;
        case 'appointments': loadAppointments(); break;
        case 'analytics':    loadAnalytics();    break;
        case 'feedback':     loadFeedback();     break;
    }
}

function refreshCurrentPage() { navigateTo(currentPage); }

// ═══════════════════════════════════════════════════════════════
//  BADGES
// ═══════════════════════════════════════════════════════════════
async function loadBadges() {
    try {
        const res  = await fetch('AdminDashboardServlet?action=getStats');
        if (!res.ok) return;
        const data = await res.json();
        if (!data.success) return;

        const pendingCount = document.getElementById('pendingCount');
        const pendingBadge = document.getElementById('pendingBadge');

        if (pendingCount) {
            const v = data.pendingCounsellors || 0;
            pendingCount.textContent   = v;
            pendingCount.style.display = v > 0 ? 'inline-flex' : 'none';
        }
        if (pendingBadge) {
            const v = data.pendingAppointments || 0;
            pendingBadge.textContent   = v;
            pendingBadge.style.display = v > 0 ? 'inline-flex' : 'none';
        }
    } catch (e) {
        console.warn('Badge load error:', e);
    }
}

// ═══════════════════════════════════════════════════════════════
//  DASHBOARD
// ═══════════════════════════════════════════════════════════════
async function loadDashboard() {
    try {
        const res  = await fetch('AdminDashboardServlet?action=getStats');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (data.success) {
            setText('totalUsers',             data.totalPatients       ?? '—');
            setText('totalCounsellors',       data.totalCounsellors    ?? '—');
            setText('totalAppointments',      data.totalAppointments   ?? '—');
            setText('pendingAppointments',    data.pendingAppointments ?? '—');
            setText('todayAppointments',      data.todayAppointments   ?? '—');
            setText('pendingCounsellorsStat', data.pendingCounsellors  ?? '—');
        }
    } catch (e) {
        console.error('Dashboard stats error:', e);
    }
    loadRecentAppointments();
}

async function loadRecentAppointments() {
    const container = document.getElementById('recentAppointments');
    if (!container) return;
    try {
        const res  = await fetch('AppointmentServlet?action=getRecent');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (data.success && data.appointments && data.appointments.length > 0) {
            container.innerHTML = buildAppointmentTable(data.appointments, true);
        } else {
            container.innerHTML = emptyState('No recent appointments found.');
        }
    } catch (e) {
        container.innerHTML = emptyState('Could not load recent appointments.');
    }
}

// ═══════════════════════════════════════════════════════════════
//  PATIENTS
// ═══════════════════════════════════════════════════════════════
async function loadUsers() {
    const body = document.getElementById('usersBody');
    if (!body) return;
    body.innerHTML = '<tr><td colspan="7" class="tbl-loading">Loading…</td></tr>';

    const search = document.getElementById('userSearch')?.value?.trim()     || '';
    const role   = document.getElementById('userRoleFilter')?.value?.trim() || '';

    try {
        let url = 'UserServlet?action=getAll';
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (role)   url += `&role=${encodeURIComponent(role)}`;

        const res  = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();

        if (data.success && data.users && data.users.length > 0) {
            body.innerHTML = data.users.map((u, i) => `
                <tr>
                    <td>${i + 1}</td>
                    <td>
                        <div style="display:flex;align-items:center;gap:9px">
                            <div style="width:32px;height:32px;border-radius:50%;background:var(--primary);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px;flex-shrink:0">
                                ${escapeHtml((u.name || 'U')[0].toUpperCase())}
                            </div>
                            <span style="font-weight:500">${escapeHtml(u.name || 'N/A')}</span>
                        </div>
                    </td>
                    <td>${escapeHtml(u.email || 'N/A')}</td>
                    <td>${escapeHtml(u.phone || '—')}</td>
                    <td><span class="role-badge role-${(u.role||'').toLowerCase()}">${escapeHtml(u.role || 'N/A')}</span></td>
                    <td>${fmtDate(u.createdAt || u.joinedAt)}</td>
                    <td>
                        <div style="display:flex;gap:6px">
                            <button class="tbl-btn edit" onclick="openEditUser(${JSON.stringify(u).replace(/"/g,'&quot;')})">
                                <i class="fa-solid fa-pen"></i>
                            </button>
                            <button class="tbl-btn delete" onclick="confirmDelete('user', ${u.id}, '${escapeHtml(u.name || '')}')">
                                <i class="fa-solid fa-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>`).join('');
        } else {
            body.innerHTML = '<tr><td colspan="7" class="tbl-loading">No patients found.</td></tr>';
        }
    } catch (e) {
        console.error('Load users error:', e);
        body.innerHTML = '<tr><td colspan="7" class="tbl-loading" style="color:#f04438">Failed to load patients.</td></tr>';
    }
}

function openAddCounsellorFromUser() { openModal('addUserModal'); }

function openEditUser(user) {
    document.getElementById('editUserId').value = user.id    || '';
    document.getElementById('editName').value   = user.name  || '';
    document.getElementById('editRole').value   = user.role  || 'patient';
    document.getElementById('editPhone').value  = user.phone || '';
    openModal('editUserModal');
}

function confirmDelete(type, id, name) {
    const msg = document.getElementById('deleteMessage');
    if (msg) msg.textContent = `Are you sure you want to delete "${name}"? This action cannot be undone.`;

    deleteCallback = async () => {
        try {
            const res  = await fetch('UserServlet', {
                method: 'POST',
                body  : new URLSearchParams({ action: 'delete', id, type })
            });
            const data = await res.json();
            if (data.success) {
                closeModal('deleteModal');
                showToast('🗑️ Deleted successfully!', 'success');
                if (type === 'counsellor') loadCounsellors();
                else loadUsers();
                loadBadges();
            } else {
                showToast(data.message || 'Delete failed.', 'error');
            }
        } catch (e) {
            showToast('Network error.', 'error');
        }
    };

    const btn = document.getElementById('confirmDeleteBtn');
    if (btn) btn.onclick = deleteCallback;
    openModal('deleteModal');
}

// ═══════════════════════════════════════════════════════════════
//  COUNSELLORS
// ═══════════════════════════════════════════════════════════════
async function loadCounsellors() {
    const body = document.getElementById('counsellorsBody');
    if (!body) return;
    body.innerHTML = '<tr><td colspan="8" class="tbl-loading">Loading…</td></tr>';

    try {
        const res  = await fetch('CounsellorServlet?action=getAll');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();

        if (data.success && data.counsellors && data.counsellors.length > 0) {
            body.innerHTML = data.counsellors.map((c, i) => {
                const status = (c.status || 'pending').toLowerCase();
                return `
                <tr>
                    <td>${i + 1}</td>
                    <td>
                        <div style="display:flex;align-items:center;gap:9px">
                            <div style="width:32px;height:32px;border-radius:50%;background:#12b87a20;color:#12b87a;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px;flex-shrink:0">
                                ${escapeHtml((c.name || 'C')[0].toUpperCase())}
                            </div>
                            <div>
                                <div style="font-weight:500">${escapeHtml(c.name || 'N/A')}</div>
                                ${c.qualification ? `<div style="font-size:11px;color:var(--muted)">${escapeHtml(c.qualification)}</div>` : ''}
                            </div>
                        </div>
                    </td>
                    <td>${escapeHtml(c.email    || 'N/A')}</td>
                    <td>${escapeHtml(c.phone    || '—')}</td>
                    <td>${escapeHtml(c.specialty|| '—')}</td>
                    <td>${c.fee ? '₹' + Number(c.fee).toLocaleString('en-IN') : '—'}</td>
                    <td>
                        <span class="status-pill ${status}">
                            ${status === 'approved' ? '✅ Approved'
                            : status === 'rejected' ? '❌ Rejected'
                            : '⏳ Pending'}
                        </span>
                    </td>
                    <td>
                        <div style="display:flex;gap:5px;flex-wrap:wrap">
                            ${status === 'pending' ? `
                                <button class="tbl-btn approve"
                                    onclick="openApproveModal(${c.id}, '${escapeHtml(c.name || '')}')"
                                    title="Review & Approve">
                                    <i class="fa-solid fa-user-check"></i> Review
                                </button>
                                <button class="tbl-btn reject"
                                    onclick="rejectCounsellor(${c.id})"
                                    title="Reject">
                                    <i class="fa-solid fa-xmark"></i>
                                </button>` : ''}
                            <button class="tbl-btn delete"
                                onclick="confirmDelete('counsellor', ${c.id}, '${escapeHtml(c.name || '')}')"
                                title="Delete">
                                <i class="fa-solid fa-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>`;
            }).join('');
        } else {
            body.innerHTML = '<tr><td colspan="8" class="tbl-loading">No counsellors found.</td></tr>';
        }
    } catch (e) {
        console.error('Load counsellors error:', e);
        body.innerHTML = '<tr><td colspan="8" class="tbl-loading" style="color:#f04438">Failed to load counsellors.</td></tr>';
    }
}

// ── Open Approve Modal ────────────────────────────────────────
function openApproveModal(id, name) {
    document.getElementById('approveCounsellorId').value         = id;
    document.getElementById('approveCounsellorName').textContent = name;
    document.getElementById('approveSpecialty').value            = '';
    document.getElementById('approveExperience').value           = '';
    document.getElementById('approveFee').value                  = '';
    openModal('approveCounsellorModal');
}

// ── Reject Counsellor ─────────────────────────────────────────
async function rejectCounsellor(id) {
    if (!confirm('Are you sure you want to reject this counsellor?')) return;
    try {
        const res  = await fetch('CounsellorServlet', {
            method: 'POST',
            body  : new URLSearchParams({ action: 'updateStatus', id, status: 'REJECTED' })
        });
        const data = await res.json();
        if (data.success) {
            showToast('❌ Counsellor rejected.', 'success');
            loadCounsellors();
            loadBadges();
        } else {
            showToast(data.message || 'Reject failed.', 'error');
        }
    } catch (e) {
        showToast('Network error.', 'error');
    }
}

function openAddCounsellor() { openModal('addUserModal'); }

// ═══════════════════════════════════════════════════════════════
//  APPOINTMENTS
// ═══════════════════════════════════════════════════════════════
async function loadAppointments() {
    const body = document.getElementById('appointmentsBody');
    if (!body) return;
    body.innerHTML = '<tr><td colspan="7" class="tbl-loading">Loading…</td></tr>';

    const search = document.getElementById('apptSearch')?.value?.trim()       || '';
    const status = document.getElementById('apptStatusFilter')?.value?.trim() || '';
    const date   = document.getElementById('apptDateFilter')?.value?.trim()   || '';

    try {
        let url = 'AppointmentServlet?action=getAll';
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (status) url += `&status=${encodeURIComponent(status)}`;
        if (date)   url += `&date=${encodeURIComponent(date)}`;

        const res  = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();

        if (data.success && data.appointments && data.appointments.length > 0) {
            body.innerHTML = data.appointments.map((a, i) => `
                <tr>
                    <td>${i + 1}</td>
                    <td><strong>${escapeHtml(a.patient       || 'N/A')}</strong></td>
                    <td>${escapeHtml(a.counsellorName || 'N/A')}</td>
                    <td>${escapeHtml(a.date           || 'N/A')}</td>
                    <td>${escapeHtml(a.time           || 'N/A')}</td>
                    <td><span class="status ${getStatusClass(a.status)}">${escapeHtml(a.status || 'Unknown')}</span></td>
                    <td>
                        <div style="display:flex;gap:6px">
                            <button class="tbl-btn view"
                                onclick='openApptDetail(${JSON.stringify(a).replace(/'/g,"\\'")})'
                                title="View Details">
                                <i class="fa-solid fa-eye"></i>
                            </button>
                            ${buildAdminStatusBtns(a)}
                        </div>
                    </td>
                </tr>`).join('');
        } else {
            body.innerHTML = '<tr><td colspan="7" class="tbl-loading">No appointments found.</td></tr>';
        }
    } catch (e) {
        console.error('Load appointments error:', e);
        body.innerHTML = '<tr><td colspan="7" class="tbl-loading" style="color:#f04438">Failed to load appointments.</td></tr>';
    }
}

function buildAdminStatusBtns(a) {
    const s = (a.status || '').toLowerCase();
    if (s === 'pending') return `
        <button class="tbl-btn approve" onclick="adminChangeStatus(${a.id},'confirmed')" title="Confirm">
            <i class="fa-solid fa-check"></i>
        </button>
        <button class="tbl-btn reject" onclick="adminChangeStatus(${a.id},'cancelled')" title="Cancel">
            <i class="fa-solid fa-xmark"></i>
        </button>`;
    if (s === 'confirmed') return `
        <button class="tbl-btn approve" onclick="adminChangeStatus(${a.id},'completed')" title="Complete">
            <i class="fa-solid fa-flag-checkered"></i>
        </button>`;
    return '';
}

async function adminChangeStatus(id, status) {
    try {
        const res  = await fetch('AppointmentServlet', {
            method: 'POST',
            body  : new URLSearchParams({ action: 'updateStatus', appointmentId: id, status })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`✅ Appointment marked as ${status}!`, 'success');
            loadAppointments();
            loadBadges();
        } else {
            showToast(data.message || 'Update failed.', 'error');
        }
    } catch (e) {
        showToast('Network error.', 'error');
    }
}

function openApptDetail(a) {
    const body = document.getElementById('apptDetailBody');
    if (!body) return;
    body.innerHTML = `
        <div style="display:grid;gap:14px">
            ${detailRow('Patient',    a.patient        || 'N/A')}
            ${detailRow('Counsellor', a.counsellorName || 'N/A')}
            ${detailRow('Date',       a.date           || 'N/A')}
            ${detailRow('Time',       a.time           || 'N/A')}
            ${detailRow('Reason',     a.reason         || 'General')}
            ${detailRow('Status',     `<span class="status ${getStatusClass(a.status)}">${escapeHtml(a.status||'Unknown')}</span>`)}
            ${a.notes ? detailRow('Notes', a.notes) : ''}
        </div>`;
    openModal('viewApptModal');
}

function detailRow(label, value) {
    return `<div style="display:flex;gap:10px;padding:10px 0;border-bottom:1px solid var(--border)">
        <span style="font-weight:600;color:var(--muted);min-width:110px;font-size:13px">${label}</span>
        <span style="color:var(--text);font-size:13.5px">${value}</span>
    </div>`;
}

function clearApptFilters() {
    const s = document.getElementById('apptSearch');       if (s) s.value = '';
    const f = document.getElementById('apptStatusFilter'); if (f) f.value = '';
    const d = document.getElementById('apptDateFilter');   if (d) d.value = '';
    loadAppointments();
}

// ═══════════════════════════════════════════════════════════════
//  ANALYTICS
// ═══════════════════════════════════════════════════════════════
async function loadAnalytics() {
    try {
        const res  = await fetch('AdminDashboardServlet?action=getStats');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (data.success) {
            setText('aUsers',       data.totalPatients       ?? '—');
            setText('aCounsellors', data.totalCounsellors    ?? '—');
            setText('aAppts',       data.totalAppointments   ?? '—');
            setText('aPending',     data.pendingAppointments ?? '—');
            renderStatusChart(data);
            renderUserChart(data);
        }
    } catch (e) {
        console.error('Analytics error:', e);
    }
}

function renderStatusChart(data) {
    const el = document.getElementById('statusChart');
    if (!el) return;
    const statuses = [
        { label:'Confirmed', value: data.confirmedAppointments || 0, color:'#12b87a' },
        { label:'Pending',   value: data.pendingAppointments   || 0, color:'#f59e0b' },
        { label:'Completed', value: data.completedAppointments || 0, color:'#3a6cf4' },
        { label:'Cancelled', value: data.cancelledAppointments || 0, color:'#f04438' }
    ];
    const total = statuses.reduce((s, i) => s + i.value, 0) || 1;
    el.innerHTML = `<div style="display:flex;flex-direction:column;gap:12px;padding:10px 0">
        ${statuses.map(s => `
            <div>
                <div style="display:flex;justify-content:space-between;margin-bottom:5px">
                    <span style="font-size:13px;font-weight:500;color:var(--text)">${s.label}</span>
                    <span style="font-size:13px;color:var(--muted)">${s.value} (${Math.round(s.value/total*100)}%)</span>
                </div>
                <div style="height:8px;background:var(--border);border-radius:999px;overflow:hidden">
                    <div style="height:100%;width:${Math.round(s.value/total*100)}%;background:${s.color};border-radius:999px;transition:width .6s ease"></div>
                </div>
            </div>`).join('')}
    </div>`;
}

function renderUserChart(data) {
    const el = document.getElementById('userChart');
    if (!el) return;
    const patients    = data.totalPatients    || 0;
    const counsellors = data.totalCounsellors || 0;
    const total       = patients + counsellors || 1;
    el.innerHTML = `<div style="display:flex;flex-direction:column;gap:20px;padding:10px 0">
        ${[
            { label:'Patients',    value: patients,    color:'#3a6cf4' },
            { label:'Counsellors', value: counsellors, color:'#12b87a' }
        ].map(item => `
            <div style="display:flex;align-items:center;gap:14px">
                <div style="width:44px;height:44px;border-radius:12px;background:${item.color}20;color:${item.color};display:flex;align-items:center;justify-content:center;font-weight:700;font-size:15px;flex-shrink:0">
                    ${item.value}
                </div>
                <div style="flex:1">
                    <div style="font-size:13px;font-weight:500;color:var(--text);margin-bottom:5px">${item.label}</div>
                    <div style="height:8px;background:var(--border);border-radius:999px;overflow:hidden">
                        <div style="height:100%;width:${Math.round(item.value/total*100)}%;background:${item.color};border-radius:999px;transition:width .6s ease"></div>
                    </div>
                </div>
                <div style="font-size:12px;color:var(--muted);flex-shrink:0">${Math.round(item.value/total*100)}%</div>
            </div>`).join('')}
    </div>`;
}

// ═══════════════════════════════════════════════════════════════
//  FEEDBACK
// ═══════════════════════════════════════════════════════════════
async function loadFeedback() {
    const grid = document.getElementById('feedbackGrid');
    if (grid) grid.innerHTML = '<p style="text-align:center;color:var(--muted);padding:40px;grid-column:1/-1">Loading feedback…</p>';

    const ratingFilter = document.getElementById('feedbackFilter')?.value || '';

    try {
        let url = 'FeedbackServlet?action=getAll';
        if (ratingFilter) url += `&rating=${ratingFilter}`;

        const res  = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();

        if (data.success) {
            setText('avgRating',       data.summary?.avgRating       ? Number(data.summary.avgRating).toFixed(1) : '—');
            setText('totalReviews',    data.summary?.totalReviews    ?? '—');
            setText('positiveReviews', data.summary?.positiveReviews ?? '—');
            setText('negativeReviews', data.summary?.negativeReviews ?? '—');

            if (grid) {
                if (data.feedbacks && data.feedbacks.length > 0) {
                    grid.innerHTML = data.feedbacks.map(f => buildFeedbackCard(f)).join('');
                } else {
                    grid.innerHTML = '<p style="text-align:center;color:var(--muted);padding:40px;grid-column:1/-1;font-style:italic">No feedback found.</p>';
                }
            }
        } else {
            if (grid) grid.innerHTML = '<p style="text-align:center;color:#f04438;padding:40px;grid-column:1/-1">Failed to load feedback.</p>';
        }
    } catch (e) {
        console.error('Feedback error:', e);
        if (grid) grid.innerHTML = '<p style="text-align:center;color:#f04438;padding:40px;grid-column:1/-1">Could not load feedback.</p>';
    }
}

function buildFeedbackCard(f) {
    const stars    = Math.min(5, Math.max(0, parseInt(f.rating) || 0));
    const starHtml = '★'.repeat(stars) + '☆'.repeat(5 - stars);
    return `
    <div style="background:var(--card);border:1px solid var(--border);border-radius:14px;padding:18px;display:flex;flex-direction:column;gap:10px">
        <div style="display:flex;justify-content:space-between;align-items:center">
            <div style="display:flex;align-items:center;gap:9px">
                <div style="width:36px;height:36px;border-radius:50%;background:var(--primary);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:14px">
                    ${escapeHtml((f.patientName || 'P')[0].toUpperCase())}
                </div>
                <div>
                    <div style="font-weight:600;font-size:14px;color:var(--text)">${escapeHtml(f.patientName || 'Anonymous')}</div>
                    <div style="font-size:11.5px;color:var(--muted)">${escapeHtml(f.counsellorName || 'Counsellor')}</div>
                </div>
            </div>
            <span style="color:#f59e0b;font-size:15px;letter-spacing:1px">${starHtml}</span>
        </div>
        ${f.comment ? `<p style="font-size:13.5px;color:var(--text);line-height:1.6;margin:0;font-style:italic">"${escapeHtml(f.comment)}"</p>` : ''}
        <div style="font-size:11px;color:var(--muted);margin-top:auto">${fmtDate(f.createdAt || f.submittedAt)}</div>
    </div>`;
}

// ═══════════════════════════════════════════════════════════════
//  MODALS
// ═══════════════════════════════════════════════════════════════
function openModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.add('active');
}
function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove('active');
}

document.addEventListener('click', e => {
    if (e.target.classList.contains('modal-overlay')) {
        e.target.classList.remove('active');
    }
});

// ═══════════════════════════════════════════════════════════════
//  TOAST
// ═══════════════════════════════════════════════════════════════
function showToast(msg, type = 'success') {
    const wrap = document.getElementById('toastWrap');
    if (!wrap) return;
    const toast = document.createElement('div');
    toast.style.cssText = `
        display:flex;align-items:center;gap:10px;
        padding:13px 18px;border-radius:10px;
        background:${type === 'success' ? '#10b981' : type === 'error' ? '#ef4444' : '#3a6cf4'};
        color:#fff;font-size:13.5px;font-weight:600;
        box-shadow:0 8px 24px rgba(0,0,0,.15);
        animation:slideUp .3s ease;margin-bottom:8px;
        pointer-events:auto;cursor:pointer;`;
    toast.innerHTML = `<i class="fa-solid fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'circle-exclamation' : 'circle-info'}"></i>${msg}`;
    toast.onclick = () => toast.remove();
    wrap.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════
function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    const div = document.createElement('div');
    div.textContent = String(text);
    return div.innerHTML;
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

function fmtDate(s) {
    if (!s) return '—';
    try { return new Date(s).toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' }); }
    catch { return s; }
}

function getStatusClass(status) {
    if (!status) return '';
    switch (status.toLowerCase().trim()) {
        case 'pending':   return 'pending';
        case 'confirmed': return 'confirmed';
        case 'completed': return 'completed';
        case 'cancelled':
        case 'canceled':  return 'cancelled';
        default:          return 'pending';
    }
}

function emptyState(msg) {
    return `<div style="text-align:center;padding:40px;color:var(--muted);font-style:italic">${escapeHtml(msg)}</div>`;
}

function buildAppointmentTable(appointments, mini = false) {
    if (!appointments || !appointments.length) return emptyState('No appointments found.');
    return `
    <table class="data-table">
        <thead>
            <tr>
                <th>#</th><th>Patient</th><th>Counsellor</th>
                <th>Date</th><th>Time</th><th>Status</th>
                ${!mini ? '<th>Actions</th>' : ''}
            </tr>
        </thead>
        <tbody>
            ${appointments.map((a, i) => `
            <tr>
                <td>${i + 1}</td>
                <td><strong>${escapeHtml(a.patient       || 'N/A')}</strong></td>
                <td>${escapeHtml(a.counsellorName || 'N/A')}</td>
                <td>${escapeHtml(a.date           || 'N/A')}</td>
                <td>${escapeHtml(a.time           || 'N/A')}</td>
                <td><span class="status ${getStatusClass(a.status)}">${escapeHtml(a.status || 'Unknown')}</span></td>
                ${!mini ? `<td>${buildAdminStatusBtns(a)}</td>` : ''}
            </tr>`).join('')}
        </tbody>
    </table>`;
}

// ── Inject styles once ────────────────────────────────────────
(function injectStyles() {
    const s = document.createElement('style');
    s.textContent = `
        @keyframes slideUp { from{opacity:0;transform:translateY(12px)} to{opacity:1;transform:translateY(0)} }
        .toast-wrap { position:fixed;bottom:24px;right:24px;z-index:9999;display:flex;flex-direction:column;pointer-events:none }
        .status-pill { display:inline-flex;align-items:center;gap:5px;padding:4px 10px;border-radius:999px;font-size:12px;font-weight:600 }
        .status-pill.approved { background:#ecfdf5;color:#12b87a }
        .status-pill.rejected { background:#fef2f2;color:#f04438 }
        .status-pill.pending  { background:#fffbeb;color:#f59e0b }
        .tbl-btn { padding:5px 10px;border-radius:7px;border:none;cursor:pointer;font-size:12px;font-weight:600;transition:.15s }
        .tbl-btn.view    { background:#eff6ff;color:#3a6cf4 }
        .tbl-btn.edit    { background:#eff6ff;color:#3a6cf4 }
        .tbl-btn.delete  { background:#fef2f2;color:#f04438 }
        .tbl-btn.approve { background:#ecfdf5;color:#12b87a }
        .tbl-btn.reject  { background:#fef2f2;color:#f04438 }
        .tbl-btn:hover   { filter:brightness(.92) }
        .role-badge { padding:3px 9px;border-radius:999px;font-size:11.5px;font-weight:600 }
        .role-patient    { background:#eff6ff;color:#3a6cf4 }
        .role-counsellor { background:#ecfdf5;color:#12b87a }
        .modal-overlay.active { display:flex }
    `;
    document.head.appendChild(s);
})();