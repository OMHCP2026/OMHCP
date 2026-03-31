// prescriptions.js

let allPrescriptions = [];

document.addEventListener('DOMContentLoaded', function() {
    loadPrescriptions();

    document.getElementById('searchInput').addEventListener('input', applyFilters);
    document.getElementById('monthFilter').addEventListener('change', applyFilters);
});

async function loadPrescriptions() {
    try {
        const res  = await fetch('/OMHCP/PrescriptionServlet?action=getMyPrescriptions');
        const data = await res.json();

        if (data.success) {
            allPrescriptions = data.prescriptions || [];
            updateStats();
            applyFilters();
        } else {
            showError(data.message || 'Failed to load prescriptions');
        }
    } catch (e) {
        console.error(e);
        showError('Network error. Please try again.');
    }
}

function updateStats() {
    document.getElementById('totalCount').textContent = allPrescriptions.length;

    const thisMonth = new Date().toISOString().substring(0, 7);
    document.getElementById('thisMonthCount').textContent =
        allPrescriptions.filter(p => (p.prescribed_date || '').startsWith(thisMonth)).length;

    const uniqueCounsellors = new Set(allPrescriptions.map(p => p.counsellor_id)).size;
    document.getElementById('counsellorCount').textContent = uniqueCounsellors;
}

function applyFilters() {
    const search = document.getElementById('searchInput').value.toLowerCase();
    const month  = document.getElementById('monthFilter').value;

    let filtered = [...allPrescriptions];

    if (search) {
        filtered = filtered.filter(p =>
            (p.medication       || '').toLowerCase().includes(search) ||
            (p.counsellor_name  || '').toLowerCase().includes(search) ||
            (p.instructions     || '').toLowerCase().includes(search)
        );
    }

    if (month !== 'all') {
        filtered = filtered.filter(p => (p.prescribed_date || '').startsWith(month));
    }

    displayPrescriptions(filtered);
}

function resetFilters() {
    document.getElementById('searchInput').value  = '';
    document.getElementById('monthFilter').value  = 'all';
    displayPrescriptions(allPrescriptions);
}

function displayPrescriptions(list) {
    const container = document.getElementById('prescriptionsList');

    if (!list || list.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-prescription-bottle"></i>
                <h3>No Prescriptions Found</h3>
                <p>Your counsellor hasn't uploaded any prescriptions yet.</p>
            </div>`;
        return;
    }

    let html = '';
    list.forEach(p => {
        const hasFile = p.file_path && p.file_path.trim() !== '';
        const fileSection = hasFile ? `
            <div class="file-attachment">
                <i class="fas fa-file-pdf"></i>
                <span>${p.file_name || 'Prescription File'}</span>
                <button class="btn-download" onclick="downloadFile('${p.file_path}', '${p.file_name}')">
                    <i class="fas fa-download"></i> Download
                </button>
            </div>` : '';

        const instructionsSection = p.instructions ? `
            <div class="instructions-box">
                <i class="fas fa-info-circle"></i>
                <strong>Instructions:</strong> ${escapeHtml(p.instructions)}
            </div>` : '';

        html += `
        <div class="prescription-card">
            <div class="card-header">
                <div class="doctor-info">
                    <h3><i class="fas fa-user-md" style="margin-right:8px;"></i>${escapeHtml(p.counsellor_name || 'Counsellor')}</h3>
                    <p><i class="fas fa-calendar" style="margin-right:6px;"></i>Prescribed on ${formatDate(p.prescribed_date)}</p>
                </div>
                <span class="card-date">${formatDate(p.prescribed_date)}</span>
            </div>

            <div class="medicine-box">
                <div class="medicine-name"><i class="fas fa-pills" style="margin-right:8px;"></i>${escapeHtml(p.medication || '')}</div>
                <div class="medicine-grid">
                    <div class="med-detail"><span>Dosage</span>${escapeHtml(p.dosage || 'N/A')}</div>
                    <div class="med-detail"><span>Frequency</span>${escapeHtml(p.frequency || 'N/A')}</div>
                    <div class="med-detail"><span>Duration</span>${escapeHtml(p.duration || 'N/A')}</div>
                </div>
                ${instructionsSection}
            </div>

            ${fileSection}
        </div>`;
    });

    container.innerHTML = html;
}

function downloadFile(filePath, fileName) {
    const link    = document.createElement('a');
    link.href     = '/OMHCP/' + filePath;
    link.download = fileName || 'prescription';
    link.click();
}

function formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showError(message) {
    document.getElementById('prescriptionsList').innerHTML = `
        <div class="empty-state">
            <i class="fas fa-exclamation-circle" style="color:#dc2626;"></i>
            <h3>Error</h3>
            <p>${escapeHtml(message)}</p>
        </div>`;
}