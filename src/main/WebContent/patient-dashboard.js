// ==================== GLOBAL VARIABLES ====================
let currentUserId = null;
let currentUser = null;
let refreshInterval = null;

// Context path for dynamic URL building (OMHCP)
const contextPath = window.location.pathname.split('/')[1]; // "OMHCP"

// ==================== INITIALIZATION ====================
document.addEventListener('DOMContentLoaded', async function() {
    console.log('✅ Patient Dashboard Loaded');
    
    // Check authentication (now async)
    const isAuthenticated = await checkAuthentication();
    if (!isAuthenticated) return; // Will redirect
    
    // Load user data (now from server-fetched user)
    loadUserData();
    
    // Load dashboard data
    loadDashboardData();
    
    // Load upcoming appointments
    loadUpcomingAppointments();
    
    // Start auto refresh (every 30 seconds)
    startAutoRefresh();
    
    // Setup event listeners
    setupEventListeners();
});

// ==================== CHECK AUTHENTICATION (UPDATED) ====================
async function checkAuthentication() {
    console.log('🔍 Checking authentication...');

    // First check localStorage (if previously saved)
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
        try {
            const user = JSON.parse(storedUser);
            if (user && user.id && user.role === 'patient') {
                console.log('✅ Using stored user data');
                currentUser = user;
                currentUserId = user.id;
                return true;
            }
        } catch (e) {
            console.warn('Stored user data corrupted, removing');
            localStorage.removeItem('user');
        }
    }

    // If not in localStorage, fetch from server using session
    console.log('📡 Fetching user from server...');
    try {
        const response = await fetch('/' + contextPath + '/PatientDashboardServlet?action=getUserInfo', {
            credentials: 'same-origin'
        });
        const data = await response.json();
        
        if (data.success && data.user) {
            currentUser = data.user;
            currentUserId = data.user.id;
            localStorage.setItem('user', JSON.stringify(data.user));
            console.log('✅ User data fetched from server and saved');
            return true;
        } else {
            console.log('❌ Not authenticated, redirecting to login');
            window.location.href = 'login.html';
            return false;
        }
    } catch (error) {
        console.error('❌ Authentication error:', error);
        window.location.href = 'login.html';
        return false;
    }
}

// ==================== LOAD USER DATA ====================
function loadUserData() {
    console.log('📡 Loading user data...');
    
    try {
        const nameElement = document.getElementById('patientName');
        const avatarElement = document.getElementById('userAvatar');
        const emailElement = document.getElementById('patientEmail');
        
        if (currentUser) {
            if (nameElement) {
                const firstName = currentUser.name.split(' ')[0];
                nameElement.textContent = firstName;
                console.log('✅ Name set to: ' + firstName);
            }
            
            if (avatarElement) {
                const initials = currentUser.name.split(' ').map(n => n[0]).join('').toUpperCase();
                avatarElement.textContent = initials;
            }
            
            if (emailElement) {
                emailElement.textContent = currentUser.email;
            }
            
            updateGreeting();
        }
    } catch (e) {
        console.error('❌ Error loading user data:', e);
    }
}

// ==================== UPDATE GREETING ====================
function updateGreeting() {
    const greetingElement = document.getElementById('greetingMessage');
    if (!greetingElement || !currentUser) return;
    
    const hour = new Date().getHours();
    let greeting = 'Good ';
    
    if (hour < 12) greeting += 'Morning';
    else if (hour < 18) greeting += 'Afternoon';
    else greeting += 'Evening';
    
    greetingElement.innerHTML = `${greeting}, ${currentUser.name.split(' ')[0]}! 👋`;
}

// ==================== LOAD DASHBOARD DATA ====================
function loadDashboardData() {
    console.log('📊 Loading dashboard data...');
    showLoading();
    fetchDashboardFromServer();
}

// ==================== FETCH FROM SERVER ====================
function fetchDashboardFromServer() {
    fetch('/' + contextPath + '/PatientDashboardServlet?action=getDashboardData&patientId=' + currentUserId, {
        credentials: 'same-origin'
    })
        .then(response => response.json())
        .then(data => {
            hideLoading();
            
            if (data.success) {
                updateStats(data.stats);
                updateAppointments(data.appointments);
                updatePrescriptions(data.prescriptions);
                showToast('Dashboard updated', 'success');
            } else {
                console.log('Using sample data...');
                loadSampleData();
            }
        })
        .catch(error => {
            hideLoading();
            console.log('Server error, using sample data:', error);
            loadSampleData();
        });
}

// ==================== LOAD SAMPLE DATA ====================
function loadSampleData() {
    console.log('📋 Loading sample data...');
    
    const stats = {
        totalAppointments: 12,
        upcomingAppointments: 3,
        completedAppointments: 9,
        totalPrescriptions: 4
    };
    
    updateStats(stats);
    
    const appointments = [
        {
            id: 1,
            doctorName: 'Dr. Sarah Smith',
            specialty: 'Clinical Psychologist',
            date: '2024-01-20',
            time: '10:00',
            status: 'confirmed',
            mode: 'video'
        },
        {
            id: 2,
            doctorName: 'Dr. Rajesh Kumar',
            specialty: 'Psychiatrist',
            date: '2024-01-21',
            time: '14:30',
            status: 'pending',
            mode: 'audio'
        },
        {
            id: 3,
            doctorName: 'Dr. Priya Patel',
            specialty: 'Counseling Psychologist',
            date: '2024-01-22',
            time: '11:00',
            status: 'confirmed',
            mode: 'video'
        }
    ];
    
    updateAppointments(appointments);
    
    const prescriptions = [
        {
            id: 1,
            doctorName: 'Dr. Sarah Smith',
            medication: 'Escitalopram',
            dosage: '10mg',
            date: '2024-01-15',
            status: 'active'
        },
        {
            id: 2,
            doctorName: 'Dr. Rajesh Kumar',
            medication: 'Alprazolam',
            dosage: '0.5mg',
            date: '2024-01-10',
            status: 'active'
        }
    ];
    
    updatePrescriptions(prescriptions);
}

// ==================== UPDATE STATS ====================
function updateStats(stats) {
    const totalEl = document.getElementById('totalAppointments');
    const upcomingEl = document.getElementById('upcomingAppointments');
    const completedEl = document.getElementById('completedAppointments');
    const prescriptionsEl = document.getElementById('totalPrescriptions');
    
    if (totalEl) totalEl.textContent = stats.totalAppointments || '0';
    if (upcomingEl) upcomingEl.textContent = stats.upcomingAppointments || '0';
    if (completedEl) completedEl.textContent = stats.completedAppointments || '0';
    if (prescriptionsEl) prescriptionsEl.textContent = stats.totalPrescriptions || '0';
}

// ==================== LOAD UPCOMING APPOINTMENTS ====================
function loadUpcomingAppointments() {
    console.log('📅 Loading upcoming appointments...');
    
    fetch('/' + contextPath + '/PatientDashboardServlet?action=getAppointments&status=upcoming&patientId=' + currentUserId, {
        credentials: 'same-origin'
    })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                updateAppointments(data.appointments);
            }
        })
        .catch(error => {
            console.log('Using sample appointments');
        });
}

// ==================== UPDATE APPOINTMENTS ====================
function updateAppointments(appointments) {
    const container = document.getElementById('appointmentsList');
    
    if (!container) return;
    
    if (!appointments || appointments.length === 0) {
        container.innerHTML = `
            <div class="no-data">
                <i class="fas fa-calendar-times"></i>
                <p>No upcoming sessions</p>
                <button class="btn-book" onclick="openBookModal()">Book Session</button>
            </div>
        `;
        return;
    }
    
    let html = '';
    appointments.slice(0, 3).forEach(app => {
        const statusClass = app.status === 'confirmed' ? 'confirmed' : 
                           app.status === 'pending' ? 'pending' : 'completed';
        
        const modeIcon = app.mode === 'video' ? 'fa-video' : 'fa-phone';
        
        html += `
            <div class="appointment-item">
                <div class="appointment-info">
                    <h4>${app.doctorName}</h4>
                    <p><i class="fas fa-calendar"></i> ${formatDate(app.date)}</p>
                    <p><i class="fas fa-clock"></i> ${app.time}</p>
                    <p><i class="fas ${modeIcon}"></i> ${app.mode} session</p>
                    <p><i class="fas fa-stethoscope"></i> ${app.specialty || 'Counselor'}</p>
                </div>
                <div class="appointment-actions">
                    <span class="status-badge ${statusClass}">${app.status}</span>
                    ${app.status === 'confirmed' ? 
                        `<button class="btn-join" onclick="joinSession(${app.id})">
                            <i class="fas fa-video"></i> Join
                        </button>` : ''}
                </div>
            </div>
        `;
    });
    
    if (appointments.length > 3) {
        html += `<div class="view-all">
            <a href="patient-appointments.html">View all appointments (${appointments.length})</a>
        </div>`;
    }
    
    container.innerHTML = html;
}

// ==================== UPDATE PRESCRIPTIONS ====================
function updatePrescriptions(prescriptions) {
    const container = document.getElementById('prescriptionsList');
    
    if (!container) return;
    
    if (!prescriptions || prescriptions.length === 0) {
        container.innerHTML = '<div class="no-data">No active prescriptions</div>';
        return;
    }
    
    let html = '';
    prescriptions.slice(0, 2).forEach(p => {
        html += `
            <div class="prescription-item">
                <h4>${p.medication} ${p.dosage}</h4>
                <p><i class="fas fa-user-md"></i> ${p.doctorName}</p>
                <p><i class="fas fa-calendar"></i> ${formatDate(p.date)}</p>
                <span class="status-badge active">${p.status}</span>
            </div>
        `;
    });
    
    if (prescriptions.length > 2) {
        html += `<div class="view-all">
            <a href="patient-prescriptions.html">View all prescriptions</a>
        </div>`;
    }
    
    container.innerHTML = html;
}

// ==================== BOOK APPOINTMENT MODAL ====================
function openBookModal() {
    const modal = document.getElementById('bookModal');
    if (modal) {
        modal.classList.add('show');
        loadCounselors();
        
        const today = new Date().toISOString().split('T')[0];
        const dateInput = document.getElementById('appointmentDate');
        if (dateInput) {
            dateInput.min = today;
            dateInput.value = today;
        }
    }
}

function closeBookModal() {
    const modal = document.getElementById('bookModal');
    if (modal) {
        modal.classList.remove('show');
    }
}

function loadCounselors() {
    const select = document.getElementById('counselorSelect');
    if (!select) return;
    
    select.innerHTML = '<option value="">Loading counselors...</option>';
    
    fetch('/' + contextPath + '/BookAppointmentServlet?action=getCounselors')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                select.innerHTML = '<option value="">Select Counselor</option>';
                data.counselors.forEach(c => {
                    select.innerHTML += `<option value="${c.id}">${c.name} - ${c.specialty} (${c.experience} yrs) - ₹${c.fee}</option>`;
                });
            } else {
                select.innerHTML = '<option value="">No counselors available</option>';
            }
        })
        .catch(error => {
            console.error('Error loading counselors:', error);
            select.innerHTML = '<option value="">Error loading counselors</option>';
        });
}

function loadAvailableSlots() {
    const counselorId = document.getElementById('counselorSelect')?.value;
    const date = document.getElementById('appointmentDate')?.value;
    const timeSelect = document.getElementById('appointmentTime');
    
    if (!timeSelect) return;
    
    if (!counselorId || !date) {
        timeSelect.innerHTML = '<option value="">Select counselor and date first</option>';
        return;
    }
    
    timeSelect.innerHTML = '<option value="">Loading slots...</option>';
    
    fetch(`/${contextPath}/BookAppointmentServlet?action=getSlots&counselorId=${counselorId}&date=${date}`)
        .then(response => response.json())
        .then(data => {
            timeSelect.innerHTML = '<option value="">Select Time</option>';
            
            if (data.success && data.slots) {
                const availableSlots = data.slots.filter(s => s.available);
                if (availableSlots.length === 0) {
                    timeSelect.innerHTML = '<option value="">No slots available</option>';
                } else {
                    availableSlots.forEach(slot => {
                        timeSelect.innerHTML += `<option value="${slot.time}">${slot.time}</option>`;
                    });
                }
            }
        })
        .catch(error => {
            console.error('Error loading slots:', error);
            timeSelect.innerHTML = '<option value="">Error loading slots</option>';
        });
}

function submitBooking() {
    const counselorId = document.getElementById('counselorSelect')?.value;
    const date = document.getElementById('appointmentDate')?.value;
    const time = document.getElementById('appointmentTime')?.value;
    const reason = document.getElementById('appointmentReason')?.value;
    
    if (!counselorId || !date || !time || !reason) {
        showToast('Please fill all fields', 'error');
        return;
    }
    
    const formData = new URLSearchParams();
    formData.append('action', 'bookAppointment');
    formData.append('counselorId', counselorId);
    formData.append('date', date);
    formData.append('time', time);
    formData.append('reason', reason);
    
    showLoading();
    
    fetch('/' + contextPath + '/BookAppointmentServlet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    })
    .then(response => response.json())
    .then(data => {
        hideLoading();
        if (data.success) {
            showToast('Appointment booked successfully!', 'success');
            closeBookModal();
            loadDashboardData();
        } else {
            showToast(data.message || 'Booking failed', 'error');
        }
    })
    .catch(error => {
        hideLoading();
        console.error('Error:', error);
        showToast('Network error', 'error');
    });
}

// ==================== JOIN SESSION ====================
function joinSession(appointmentId) {
    showToast('Starting video session...', 'info');
    window.open(`video-call.html?appointment=${appointmentId}`, '_blank');
}

// ==================== FORMAT DATE ====================
function formatDate(dateStr) {
    if (!dateStr) return '';
    
    const today = new Date().toISOString().split('T')[0];
    const tomorrow = new Date(Date.now() + 86400000).toISOString().split('T')[0];
    
    if (dateStr === today) return 'Today';
    if (dateStr === tomorrow) return 'Tomorrow';
    
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

// ==================== TOAST NOTIFICATIONS ====================
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    
    const toastId = 'toast_' + Date.now();
    const toast = document.createElement('div');
    toast.id = toastId;
    toast.className = `toast ${type}`;
    
    const icons = {
        'success': 'fa-check-circle',
        'error': 'fa-exclamation-circle',
        'warning': 'fa-exclamation-triangle',
        'info': 'fa-info-circle'
    };
    
    toast.innerHTML = `
        <div class="toast-icon">
            <i class="fas ${icons[type]}"></i>
        </div>
        <div class="toast-content">
            <div class="toast-title">${type.charAt(0).toUpperCase() + type.slice(1)}</div>
            <div class="toast-message">${message}</div>
        </div>
        <button class="toast-close" onclick="closeToast('${toastId}')">
            <i class="fas fa-times"></i>
        </button>
    `;
    
    container.appendChild(toast);
    setTimeout(() => toast.classList.add('show'), 10);
    setTimeout(() => closeToast(toastId), 3000);
}

function closeToast(toastId) {
    const toast = document.getElementById(toastId);
    if (toast) {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }
}

// ==================== LOADING OVERLAY ====================
function showLoading() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) overlay.classList.add('show');
}

function hideLoading() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) overlay.classList.remove('show');
}

// ==================== AUTO REFRESH ====================
function startAutoRefresh() {
    if (refreshInterval) clearInterval(refreshInterval);
    
    // Dashboard data refresh every 30 seconds
    refreshInterval = setInterval(() => {
        console.log('🔄 Auto-refreshing data...');
        loadDashboardData();
    }, 30000);

    // ✅ Incoming call check every 3 seconds
    setInterval(checkIncomingCall, 3000);
}

// ==================== SETUP EVENT LISTENERS ====================
function setupEventListeners() {
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) logoutBtn.addEventListener('click', handleLogout);
    
    const bookBtn = document.getElementById('bookSessionBtn');
    if (bookBtn) bookBtn.addEventListener('click', openBookModal);
    
    const dateInput = document.getElementById('appointmentDate');
    if (dateInput) dateInput.addEventListener('change', loadAvailableSlots);
    
    const counselorSelect = document.getElementById('counselorSelect');
    if (counselorSelect) counselorSelect.addEventListener('change', loadAvailableSlots);
    
    const toggleBtn = document.getElementById('sidebarToggle');
    if (toggleBtn) toggleBtn.addEventListener('click', toggleSidebar);
}

// ==================== LOGOUT ====================
function handleLogout(event) {
    if (event) event.preventDefault();

    showToast('Logging out...', 'info');

    if (refreshInterval) clearInterval(refreshInterval);

    localStorage.removeItem('user');
    sessionStorage.clear();

    fetch('/' + contextPath + '/LogoutServlet', { 
        method: 'POST', 
        credentials: 'same-origin' 
    })
    .finally(() => {
        setTimeout(() => {
            window.location.href = 'login.html';
        }, 500);
    });

    return false;
}

// ==================== SIDEBAR TOGGLE ====================
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    if (sidebar) sidebar.classList.toggle('active');
}

// ==================== INCOMING CALL SYSTEM ====================
let callOverlay = null;
let ringtoneInterval = null;
let audioCtx = null;

function checkIncomingCall() {
    fetch('/' + contextPath + '/CallServlet?action=checkIncomingCall', {
        credentials: 'same-origin'
    })
    .then(r => r.json())
    .then(data => {
        if (data.hasCall && !callOverlay) {
            // New incoming call — show overlay
            showCallOverlay(data.appointmentId, data.callerName || 'Your Counsellor');
        } else if (!data.hasCall && callOverlay) {
            // Caller hung up — dismiss overlay
            dismissCallOverlay();
            showToast('Missed call from counsellor', 'warning');
        }
    })
    .catch(() => {}); // Silent fail — don't spam errors
}

function showCallOverlay(appointmentId, callerName) {
    if (callOverlay) return; // Already showing

    startRingtone();

    callOverlay = document.createElement('div');
    callOverlay.id = 'omhcp-call-overlay';
    callOverlay.style.cssText = `
        position: fixed; inset: 0; z-index: 100000;
        background: rgba(10, 20, 40, 0.85);
        backdrop-filter: blur(6px);
        display: flex; align-items: center; justify-content: center;
        animation: omhcpFadeIn 0.4s ease;
    `;

    callOverlay.innerHTML = `
        <style>
            @keyframes omhcpFadeIn { from { opacity: 0; } to { opacity: 1; } }
            @keyframes omhcpPulse  { 0% { box-shadow: 0 0 0 0 rgba(84,160,255,0.5); } 70% { box-shadow: 0 0 0 20px rgba(84,160,255,0); } 100% { box-shadow: 0 0 0 0 rgba(84,160,255,0); } }
            @keyframes omhcpPopIn  { from { transform: scale(0.7); opacity: 0; } to { transform: scale(1); opacity: 1; } }
            .omhcp-call-btn { transition: transform 0.15s, opacity 0.15s; }
            .omhcp-call-btn:hover { transform: scale(1.1); opacity: 0.9; }
            .omhcp-call-btn:active { transform: scale(0.95); }
        </style>

        <div style="
            background: linear-gradient(160deg, #0f2944, #1a3a5c);
            border: 1px solid rgba(255,255,255,0.12);
            border-radius: 24px;
            padding: 48px 56px;
            text-align: center;
            box-shadow: 0 24px 80px rgba(0,0,0,0.5);
            color: #fff;
            font-family: 'Segoe UI', sans-serif;
            min-width: 320px;
            animation: omhcpPopIn 0.45s cubic-bezier(0.34, 1.56, 0.64, 1);
        ">
            <!-- Avatar -->
            <div style="
                width: 90px; height: 90px; border-radius: 50%;
                background: linear-gradient(135deg, #2e86de, #54a0ff);
                margin: 0 auto 20px;
                display: flex; align-items: center; justify-content: center;
                font-size: 40px;
                animation: omhcpPulse 1.5s ease-in-out infinite;
            ">📹</div>

            <!-- Labels -->
            <div style="font-size:13px; letter-spacing:2px; text-transform:uppercase; color:#7fb3e8; margin-bottom:8px;">
                Incoming Video Call
            </div>
            <div style="font-size:22px; font-weight:700; margin-bottom:6px;">${callerName}</div>
            <div style="font-size:14px; color:rgba(255,255,255,0.55); margin-bottom:36px;">
                Appointment #${appointmentId}
            </div>

            <!-- Buttons -->
            <div style="display:flex; gap:28px; justify-content:center;">
                <!-- Decline -->
                <div style="display:flex; flex-direction:column; align-items:center; gap:8px;">
                    <button class="omhcp-call-btn" onclick="declineCall()" style="
                        width:64px; height:64px; border-radius:50%; border:none; cursor:pointer;
                        font-size:26px;
                        background: linear-gradient(135deg, #c0392b, #e74c3c);
                        box-shadow: 0 8px 24px rgba(231,76,60,0.4);
                    ">📵</button>
                    <span style="font-size:11px; color:rgba(255,255,255,0.6);">Decline</span>
                </div>

                <!-- Accept -->
                <div style="display:flex; flex-direction:column; align-items:center; gap:8px;">
                    <button class="omhcp-call-btn" onclick="acceptCall(${appointmentId})" style="
                        width:64px; height:64px; border-radius:50%; border:none; cursor:pointer;
                        font-size:26px;
                        background: linear-gradient(135deg, #1e9e5c, #27ae60);
                        box-shadow: 0 8px 24px rgba(39,174,96,0.4);
                    ">📞</button>
                    <span style="font-size:11px; color:rgba(255,255,255,0.6);">Accept</span>
                </div>
            </div>
        </div>
    `;

    document.body.appendChild(callOverlay);
}

function dismissCallOverlay() {
    stopRingtone();
    if (callOverlay) {
        callOverlay.remove();
        callOverlay = null;
    }
}

function acceptCall(appointmentId) {
    dismissCallOverlay();
    showToast('Joining video call...', 'success');
    window.open(`video-call.html?appointment=${appointmentId}`, '_blank');
}

function declineCall() {
    dismissCallOverlay();
    showToast('Call declined', 'info');

    // Optional: notify server that patient declined
    fetch('/' + contextPath + '/CallServlet?action=declineCall', {
        credentials: 'same-origin'
    }).catch(() => {});
}

// ==================== RINGTONE (Web Audio API) ====================
function startRingtone() {
    try {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        let count = 0;
        ringtoneInterval = setInterval(() => {
            if (count++ > 30) { stopRingtone(); return; } // Stop after 30 seconds
            const osc  = audioCtx.createOscillator();
            const gain = audioCtx.createGain();
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            osc.type = 'sine';
            osc.frequency.setValueAtTime(880, audioCtx.currentTime);
            osc.frequency.setValueAtTime(660, audioCtx.currentTime + 0.15);
            gain.gain.setValueAtTime(0.3, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.5);
            osc.start(audioCtx.currentTime);
            osc.stop(audioCtx.currentTime + 0.5);
        }, 1000);
    } catch (e) {
        console.warn('Audio not supported:', e);
    }
}

function stopRingtone() {
    if (ringtoneInterval) { clearInterval(ringtoneInterval); ringtoneInterval = null; }
    if (audioCtx)         { audioCtx.close(); audioCtx = null; }
}

// ==================== MAKE FUNCTIONS GLOBAL ====================
window.handleLogout       = handleLogout;
window.toggleSidebar      = toggleSidebar;
window.openBookModal      = openBookModal;
window.closeBookModal     = closeBookModal;
window.loadAvailableSlots = loadAvailableSlots;
window.submitBooking      = submitBooking;
window.joinSession        = joinSession;
window.showToast          = showToast;
window.closeToast         = closeToast;
window.acceptCall         = acceptCall;
window.declineCall        = declineCall;