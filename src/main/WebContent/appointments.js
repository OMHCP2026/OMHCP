// Global variables
let allAppointments = [];
let filteredAppointments = [];
let currentUserRole = null; // 'patient' or 'counsellor'

// Load appointments on page load
document.addEventListener('DOMContentLoaded', function() {
    console.log('✅ Appointments page loaded');
    detectRoleAndLoad();
    setupEventListeners();
});

// ─── Step 1: Detect role from server, then load appointments ────────────────
function detectRoleAndLoad() {
    fetch('SessionServlet?action=getRole')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                currentUserRole = data.role; // 'patient' or 'counsellor'
                console.log('👤 Role detected:', currentUserRole);
                adaptUIForRole(currentUserRole);
                loadAppointments();
            } else {
                // Not logged in → redirect to login
                window.location.href = 'login.html';
            }
        })
        .catch(() => {
            // Fallback: try loading as patient
            currentUserRole = 'patient';
            loadAppointments();
        });
}

// ─── Step 2: Adapt UI labels based on role ──────────────────────────────────
function adaptUIForRole(role) {
    const searchInput = document.getElementById('searchInput');
    if (role === 'counsellor') {
        // Counsellor sees patient names
        if (searchInput) searchInput.placeholder = 'Search by patient name...';
        document.querySelector('.header h1').innerHTML =
            '<i class="fas fa-calendar-check"></i> My Appointments';
    } else {
        // Patient sees doctor names
        if (searchInput) searchInput.placeholder = 'Search by doctor name...';
    }
}

// ─── Step 3: Fetch appointments from servlet ────────────────────────────────
function loadAppointments() {
    showLoading();

    // Patient uses action=getMyAppointments, counsellor uses default (no action)
    const url = currentUserRole === 'patient'
        ? 'AppointmentServlet?action=getMyAppointments'
        : 'AppointmentServlet';

    fetch(url)
        .then(response => response.json())
        .then(data => {
            hideLoading();
            console.log('📊 Appointments data:', data);

            if (data.success) {
                allAppointments = data.appointments || [];
                filteredAppointments = [...allAppointments];
                displayAppointments();
                updateStats();
                updateCount();
            } else {
                showToast(data.message || 'Failed to load appointments', 'error');
                document.getElementById('appointmentsContainer').innerHTML = `
                    <div class="empty-state">
                        <i class="fas fa-exclamation-circle"></i>
                        <h3>Error Loading Appointments</h3>
                        <p>${data.message || 'Please try again'}</p>
                    </div>
                `;
            }
        })
        .catch(error => {
            console.error('❌ Error:', error);
            hideLoading();
            showToast('Network error occurred.', 'error');
            showErrorState();
        });
}

function showErrorState() {
    document.getElementById('appointmentsContainer').innerHTML = `
        <div class="empty-state">
            <i class="fas fa-wifi"></i>
            <h3>Connection Error</h3>
            <p>Could not connect to the server. Please refresh and try again.</p>
        </div>
    `;
}

// ─── Display appointments (role-aware) ──────────────────────────────────────
function displayAppointments() {
    const container = document.getElementById('appointmentsContainer');
    if (!container) return;

    if (!filteredAppointments || filteredAppointments.length === 0) {
        const bookBtn = currentUserRole === 'patient'
            ? `<button class="btn-primary" onclick="window.location.href='book-appointment.html'">
                   <i class="fas fa-calendar-plus"></i> Book New Appointment
               </button>`
            : '';

        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-calendar-times"></i>
                <h3>No Appointments Found</h3>
                <p>You don't have any appointments yet.</p>
                ${bookBtn}
            </div>
        `;
        return;
    }

    let html = '';
    filteredAppointments.forEach(app => {
        const statusClass = getStatusClass(app.status);
        const displayDate = formatDate(app.date);

        // Patient sees doctor name + specialty
        // Counsellor sees patient name (no specialty field)
        const nameLabel    = currentUserRole === 'patient'
            ? (app.doctorName || 'Counselor')
            : (app.patient || 'Patient');

        const specialtyRow = currentUserRole === 'patient'
            ? `<div class="specialty">
                   <i class="fas fa-stethoscope"></i> ${app.specialty || 'Mental Health Counselor'}
               </div>`
            : `<div class="specialty">
                   <i class="fas fa-user"></i> Patient
               </div>`;

        const canCancel = app.status === 'pending' || app.status === 'confirmed';

        html += `
            <div class="appointment-card" data-id="${app.id}">
                <div class="appointment-time">
                    <span class="date">${displayDate}</span>
                    <span class="time">${formatTime(app.time)}</span>
                </div>

                <div class="appointment-info">
                    <h3>${nameLabel}</h3>
                    ${specialtyRow}
                    <div class="reason">
                        <i class="fas fa-notes-medical"></i> ${app.reason || 'No reason provided'}
                    </div>
                </div>

                <div class="appointment-status">
                    <span class="status-badge ${statusClass}">${app.status}</span>
                </div>

                <div class="appointment-actions">
                    ${canCancel ? `
                        <button class="action-btn cancel" onclick="openCancelModal(${app.id})" title="Cancel">
                            <i class="fas fa-times"></i>
                        </button>
                    ` : ''}
                    <button class="action-btn" onclick="viewDetails(${app.id})" title="View Details">
                        <i class="fas fa-eye"></i>
                    </button>
                </div>
            </div>
        `;
    });

    container.innerHTML = html;
}

// ─── Stats ───────────────────────────────────────────────────────────────────
function updateStats() {
    const total     = allAppointments.length;
    const upcoming  = allAppointments.filter(a => a.status === 'pending' || a.status === 'confirmed').length;
    const completed = allAppointments.filter(a => a.status === 'completed').length;
    const cancelled = allAppointments.filter(a => a.status === 'cancelled').length;

    document.getElementById('totalCount').textContent     = total;
    document.getElementById('upcomingCount').textContent  = upcoming;
    document.getElementById('completedCount').textContent = completed;
    document.getElementById('cancelledCount').textContent = cancelled;
}

function updateCount() {
    const count = filteredAppointments.length;
    document.getElementById('appointmentCount').textContent =
        `${count} appointment${count !== 1 ? 's' : ''}`;
}

// ─── Filters & Tabs ──────────────────────────────────────────────────────────
function filterAppointments(type) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');

    if (type === 'all') {
        filteredAppointments = [...allAppointments];
    } else if (type === 'upcoming') {
        filteredAppointments = allAppointments.filter(a => a.status === 'pending' || a.status === 'confirmed');
    } else {
        filteredAppointments = allAppointments.filter(a => a.status === type);
    }

    displayAppointments();
    updateCount();
}

function resetFilters() {
    document.getElementById('searchInput').value  = '';
    document.getElementById('monthFilter').value  = 'all';

    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
        if (btn.textContent.includes('All')) btn.classList.add('active');
    });

    filteredAppointments = [...allAppointments];
    displayAppointments();
    updateCount();
    showToast('Filters reset', 'info');
}

function setupEventListeners() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('input', function(e) {
            const term = e.target.value.toLowerCase();
            filteredAppointments = allAppointments.filter(app => {
                // Search by doctor name (patient) or patient name (counsellor)
                const name = currentUserRole === 'patient'
                    ? (app.doctorName || '')
                    : (app.patient || '');
                return name.toLowerCase().includes(term);
            });
            displayAppointments();
            updateCount();
        });
    }

    const monthFilter = document.getElementById('monthFilter');
    if (monthFilter) {
        monthFilter.addEventListener('change', function(e) {
            const month = e.target.value;
            if (month === 'all') {
                filteredAppointments = [...allAppointments];
            } else {
                filteredAppointments = allAppointments.filter(app => {
                    return new Date(app.date).getMonth() === parseInt(month);
                });
            }
            displayAppointments();
            updateCount();
        });
    }
}

// ─── Cancel ──────────────────────────────────────────────────────────────────
function openCancelModal(id) {
    document.getElementById('cancelId').value = id;
    document.getElementById('cancelModal').classList.add('active');
}

function closeCancelModal() {
    document.getElementById('cancelModal').classList.remove('active');
}

function confirmCancel() {
    const id = document.getElementById('cancelId').value;
    showLoading();

    const formData = new URLSearchParams();
    formData.append('action', 'cancel');
    formData.append('appointmentId', id);

    fetch('AppointmentServlet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    })
    .then(response => response.json())
    .then(data => {
        hideLoading();
        if (data.success) {
            showToast('✅ ' + data.message, 'success');
            closeCancelModal();
            loadAppointments();
        } else {
            showToast('❌ ' + data.message, 'error');
        }
    })
    .catch(error => {
        hideLoading();
        console.error('Error:', error);
        showToast('Network error', 'error');
    });
}

// ─── View Details ─────────────────────────────────────────────────────────────
function viewDetails(id) {
    const appointment = allAppointments.find(a => a.id == id);
    if (appointment) {
        const name = currentUserRole === 'patient'
            ? appointment.doctorName
            : appointment.patient;
        showToast(`Viewing details for ${name}`, 'info');
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
function getStatusClass(status) {
    switch(status?.toLowerCase()) {
        case 'confirmed': return 'confirmed';
        case 'pending':   return 'pending';
        case 'completed': return 'completed';
        case 'cancelled': return 'cancelled';
        default: return '';
    }
}

function formatDate(dateString) {
    if (!dateString) return '';
    const today    = new Date().toISOString().split('T')[0];
    const tomorrow = new Date(Date.now() + 86400000).toISOString().split('T')[0];
    if (dateString === today)    return 'Today';
    if (dateString === tomorrow) return 'Tomorrow';
    return new Date(dateString).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function formatTime(timeString) {
    if (!timeString) return '';
    const [hours, minutes] = timeString.split(':');
    const hour = parseInt(hours);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    return `${hour % 12 || 12}:${minutes} ${ampm}`;
}

function showLoading() {
    document.getElementById('loadingSpinner').style.display = 'block';
}

function hideLoading() {
    document.getElementById('loadingSpinner').style.display = 'none';
}

function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    const toastMessage = document.getElementById('toastMessage');
    toast.className = 'toast ' + type;
    toastMessage.textContent = message;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 3000);
}

// Make functions global
window.filterAppointments = filterAppointments;
window.resetFilters       = resetFilters;
window.openCancelModal    = openCancelModal;
window.closeCancelModal   = closeCancelModal;
window.confirmCancel      = confirmCancel;
window.viewDetails        = viewDetails;