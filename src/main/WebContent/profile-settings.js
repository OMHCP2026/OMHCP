// profile-settings.js

document.addEventListener('DOMContentLoaded', loadProfile);

// ── Load Profile ──────────────────────────────────────────────────────────────
async function loadProfile() {
    try {
        const res  = await fetch('/OMHCP/ProfileServlet?action=get');
        const data = await res.json();
        if (data.success) {
            fillForm(data.profile);
        } else {
            showAlert('Could not load profile: ' + (data.message || data.error || 'Unknown error'), 'error');
        }
    } catch(e) {
        showAlert('Network error: ' + e.message, 'error');
    }
}

function fillForm(p) {
    document.getElementById('fullName').value       = p.name           || '';
    document.getElementById('email').value          = p.email          || '';
    document.getElementById('phone').value          = p.phone          || '';
    document.getElementById('specialization').value = p.specialization || '';
    document.getElementById('experience').value     = p.experience     || '';
    document.getElementById('languages').value      = p.languages      || '';
    document.getElementById('bio').value            = p.bio            || '';

    // Header
    document.getElementById('headerName').textContent  = p.name  || 'Counsellor';
    document.getElementById('headerEmail').textContent = p.email || '';
    document.getElementById('headerRole').textContent  = p.role  || 'Counsellor';

    // Avatar initials
    const initials = getInitials(p.name);
    document.getElementById('avatarInitials').textContent = initials;

    // Photo if available
    if (p.photoUrl) {
        const circle = document.getElementById('avatarCircle');
        circle.innerHTML = `<img src="${p.photoUrl}" alt="Profile Photo"/>`;
    }

    // Back link
    const backLink = document.getElementById('backLink');
    if (backLink && p.role) {
        backLink.href = p.role.toLowerCase() === 'patient'
            ? 'patient-dashboard.html'
            : 'counsellor-dashboard.html';
    }
}

// ── Save Personal Info ────────────────────────────────────────────────────────
async function savePersonalInfo() {
    hideAlert();
    const formData = new FormData();
    formData.append('action',         'updateProfile');
    formData.append('name',           document.getElementById('fullName').value.trim());
    formData.append('email',          document.getElementById('email').value.trim());
    formData.append('phone',          document.getElementById('phone').value.trim());
    formData.append('specialization', document.getElementById('specialization').value);
    formData.append('experience',     document.getElementById('experience').value.trim());
    formData.append('languages',      document.getElementById('languages').value.trim());
    formData.append('bio',            document.getElementById('bio').value.trim());

    // Photo file if selected
    const photoInput = document.getElementById('photoInput');
    if (photoInput.files.length > 0) {
        formData.append('photo', photoInput.files[0]);
    }

    try {
        const res  = await fetch('/OMHCP/ProfileServlet', { method:'POST', body:formData });
        const data = await res.json();
        console.log('Server response:', data); // 👈 See this in console (F12)
        if (data.success) {
            showAlert('✅ Profile updated successfully!', 'success');
            // Update header name live
            document.getElementById('headerName').textContent =
                document.getElementById('fullName').value.trim();
            document.getElementById('avatarInitials').textContent =
                getInitials(document.getElementById('fullName').value.trim());
        } else {
            // Show the exact error from server (message or error field)
            const errorMsg = data.message || data.error || 'Update failed';
            showAlert('❌ ' + errorMsg, 'error');
        }
    } catch(e) {
        showAlert('❌ Network error: ' + e.message, 'error');
    }
}

// ── Change Password ───────────────────────────────────────────────────────────
async function changePassword() {
    hideAlert();
    const current  = document.getElementById('currentPassword').value;
    const newPwd   = document.getElementById('newPassword').value;
    const confirm  = document.getElementById('confirmPassword').value;

    if (!current || !newPwd || !confirm) {
        showAlert('❌ All password fields are required.', 'error'); return;
    }
    if (newPwd.length < 8) {
        showAlert('❌ New password must be at least 8 characters.', 'error'); return;
    }
    if (newPwd !== confirm) {
        showAlert('❌ Passwords do not match.', 'error'); return;
    }

    const formData = new FormData();
    formData.append('action',          'changePassword');
    formData.append('currentPassword', current);
    formData.append('newPassword',     newPwd);

    try {
        const res  = await fetch('/OMHCP/ProfileServlet', { method:'POST', body:formData });
        const data = await res.json();
        console.log('Server response:', data);
        if (data.success) {
            showAlert('✅ Password changed successfully!', 'success');
            document.getElementById('currentPassword').value = '';
            document.getElementById('newPassword').value     = '';
            document.getElementById('confirmPassword').value = '';
            document.getElementById('strengthBar').style.width = '0';
        } else {
            const errorMsg = data.message || data.error || 'Password update failed';
            showAlert('❌ ' + errorMsg, 'error');
        }
    } catch(e) {
        showAlert('❌ Network error: ' + e.message, 'error');
    }
}

// ── Deactivate Account ────────────────────────────────────────────────────────
async function deactivateAccount() {
    try {
        const formData = new FormData();
        formData.append('action', 'deactivate');
        const res  = await fetch('/OMHCP/ProfileServlet', { method:'POST', body:formData });
        const data = await res.json();
        console.log('Server response:', data);
        if (data.success) {
            alert('Account deactivated. You will be logged out.');
            window.location.href = 'login.html';
        } else {
            const errorMsg = data.message || data.error || 'Could not deactivate';
            showAlert('❌ ' + errorMsg, 'error');
        }
    } catch(e) {
        showAlert('❌ ' + e.message, 'error');
    }
}

// ── Photo preview ─────────────────────────────────────────────────────────────
function previewPhoto(input) {
    if (!input.files.length) return;
    const reader = new FileReader();
    reader.onload = e => {
        const circle = document.getElementById('avatarCircle');
        circle.innerHTML = `<img src="${e.target.result}" alt="Preview"/>`;
    };
    reader.readAsDataURL(input.files[0]);
}

// ── Password strength ─────────────────────────────────────────────────────────
function checkStrength(pwd) {
    const bar = document.getElementById('strengthBar');
    let score = 0;
    if (pwd.length >= 8)               score++;
    if (/[A-Z]/.test(pwd))             score++;
    if (/[0-9]/.test(pwd))             score++;
    if (/[^A-Za-z0-9]/.test(pwd))     score++;

    const widths = ['0%','30%','55%','80%','100%'];
    const colors = ['#e5e7eb','#ef4444','#f97316','#eab308','#22c55e'];
    bar.style.width      = widths[score];
    bar.style.background = colors[score];
}

// ── Alert helpers ─────────────────────────────────────────────────────────────
function showAlert(msg, type) {
    const box = document.getElementById('alertBox');
    box.textContent = msg;
    box.className   = `alert ${type}`;
    box.scrollIntoView({ behavior:'smooth', block:'nearest' });
    setTimeout(hideAlert, 4000);
}
function hideAlert() {
    const box = document.getElementById('alertBox');
    box.className   = 'alert';
    box.textContent = '';
}

// ── Initials ──────────────────────────────────────────────────────────────────
function getInitials(name) {
    if (!name) return 'DR';
    return name.trim().split(' ').map(w => w[0]).join('').toUpperCase().substring(0,2);
}