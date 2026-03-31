// prescription-upload.js

document.addEventListener('DOMContentLoaded', function() {
    setDefaultDate();
    setupFileUpload();
    setupPatientSearch();
    setupForm();

    // Pre-fill from URL params (coming from dashboard Upload button)
    const params = new URLSearchParams(window.location.search);
    if (params.get('appointmentId')) {
        document.getElementById('appointmentId').value = params.get('appointmentId');
    }
    if (params.get('patient')) {
        document.getElementById('patientSearch').value = decodeURIComponent(params.get('patient'));
        document.getElementById('selectedPatient').value = decodeURIComponent(params.get('patient'));
    }
});

function setDefaultDate() {
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('prescribedDate').value = today;
}

// ── Patient Search ────────────────────────────────────────────────────────────
function setupPatientSearch() {
    const input   = document.getElementById('patientSearch');
    const results = document.getElementById('patientResults');

    input.addEventListener('input', async function() {
        const term = this.value.trim();
        if (term.length < 2) { results.style.display = 'none'; return; }

        try {
            const res  = await fetch(`/OMHCP/UserSearchServlet?role=patient&q=${encodeURIComponent(term)}`);
            const data = await res.json();

            if (data.success && data.users.length > 0) {
                results.innerHTML = data.users.map(u =>
                    `<div class="patient-result-item" onclick="selectPatient(${u.id}, '${escapeHtml(u.name)}')">
                        <i class="fas fa-user" style="color:#2563eb;margin-right:8px;"></i>${u.name}
                     </div>`
                ).join('');
                results.style.display = 'block';
            } else {
                results.innerHTML = '<div class="patient-result-item" style="color:#9ca3af;">No patients found</div>';
                results.style.display = 'block';
            }
        } catch (e) {
            console.error('Search error:', e);
        }
    });

    // Hide on outside click
    document.addEventListener('click', function(e) {
        if (!input.contains(e.target) && !results.contains(e.target)) {
            results.style.display = 'none';
        }
    });
}

function selectPatient(id, name) {
    document.getElementById('patientId').value       = id;
    document.getElementById('selectedPatient').value = name;
    document.getElementById('patientSearch').value   = name;
    document.getElementById('patientResults').style.display = 'none';

    // Load appointments for this patient
    loadPatientAppointments(id);
}

async function loadPatientAppointments(patientId) {
    try {
        const res  = await fetch(`/OMHCP/AppointmentServlet?action=getPatientAppointmentsForCounsellor&patientId=${patientId}`);
        const data = await res.json();
        const sel  = document.getElementById('appointmentId');

        sel.innerHTML = '<option value="0">-- Select Appointment --</option>';
        if (data.success && data.appointments.length > 0) {
            data.appointments.forEach(a => {
                sel.innerHTML += `<option value="${a.id}">${a.date} ${a.time} - ${a.reason || 'Consultation'}</option>`;
            });
        }
    } catch (e) {
        console.error('Load appointments error:', e);
    }
}

// ── File Upload ───────────────────────────────────────────────────────────────
function setupFileUpload() {
    const area    = document.getElementById('fileUploadArea');
    const input   = document.getElementById('prescriptionFile');
    const display = document.getElementById('fileNameDisplay');

    input.addEventListener('change', function() {
        if (this.files[0]) {
            display.textContent = '📎 ' + this.files[0].name;
        }
    });

    area.addEventListener('dragover',  e => { e.preventDefault(); area.classList.add('dragover'); });
    area.addEventListener('dragleave', () => area.classList.remove('dragover'));
    area.addEventListener('drop', e => {
        e.preventDefault();
        area.classList.remove('dragover');
        input.files = e.dataTransfer.files;
        if (e.dataTransfer.files[0]) {
            display.textContent = '📎 ' + e.dataTransfer.files[0].name;
        }
    });
}

// ── Form Submit ───────────────────────────────────────────────────────────────
function setupForm() {
    document.getElementById('prescriptionForm').addEventListener('submit', async function(e) {
        e.preventDefault();

        // Validate
        const patientId  = document.getElementById('patientId').value;
        const medication = document.getElementById('medication').value.trim();
        const date       = document.getElementById('prescribedDate').value;

        if (!patientId) { showToast('Please select a patient', 'error'); return; }
        if (!medication) { showToast('Please enter medication name', 'error'); return; }
        if (!date) { showToast('Please select prescribed date', 'error'); return; }

        const btn = document.getElementById('submitBtn');
        btn.disabled    = true;
        btn.innerHTML   = '<i class="fas fa-spinner fa-spin"></i> Uploading...';

        try {
            const formData = new FormData(this);
            const res      = await fetch('/OMHCP/PrescriptionServlet', {
                method: 'POST',
                body:   formData
            });
            const data = await res.json();

            if (data.success) {
                showToast('Prescription sent successfully!', 'success');
                setTimeout(() => window.location.href = 'counsellor-dashboard.html', 2000);
            } else {
                showToast(data.message || 'Upload failed', 'error');
                btn.disabled  = false;
                btn.innerHTML = '<i class="fas fa-paper-plane"></i> Send Prescription to Patient';
            }
        } catch (err) {
            console.error(err);
            showToast('Network error. Please try again.', 'error');
            btn.disabled  = false;
            btn.innerHTML = '<i class="fas fa-paper-plane"></i> Send Prescription to Patient';
        }
    });
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function showToast(msg, type) {
    const toast = document.getElementById('toast');
    const icon  = document.getElementById('toastIcon');
    document.getElementById('toastMsg').textContent = msg;
    icon.className = type === 'success' ? 'fas fa-check-circle' : 'fas fa-times-circle';
    toast.className = 'toast ' + type;
    setTimeout(() => { toast.style.display = 'none'; toast.className = 'toast'; }, 3500);
}

function escapeHtml(str) {
    return str.replace(/'/g, "\\'").replace(/"/g, '\\"');
}