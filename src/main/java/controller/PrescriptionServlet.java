package controller;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;
import com.healthcare.util.DBConnection;

@WebServlet("/PrescriptionServlet")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,      // 1MB
    maxFileSize       = 10 * 1024 * 1024, // 10MB
    maxRequestSize    = 15 * 1024 * 1024  // 15MB
)
public class PrescriptionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // ── GET: fetch prescriptions ──────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Please login first\"}");
            return;
        }

        String role   = ((String) session.getAttribute("userRole")).trim();
        int    userId = (int) session.getAttribute("userId");
        String action = request.getParameter("action");

        if ("getMyPrescriptions".equals(action) && "patient".equalsIgnoreCase(role)) {
            out.print(getPatientPrescriptions(userId));
        } else if ("getCounsellorPrescriptions".equals(action) && "counsellor".equalsIgnoreCase(role)) {
            out.print(getCounsellorPrescriptions(userId));
        } else {
            out.print("{\"success\":false,\"message\":\"Access denied\"}");
        }
    }

    // ── POST: upload prescription ─────────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Please login first\"}");
            return;
        }

        String role = ((String) session.getAttribute("userRole")).trim();
        if (!"counsellor".equalsIgnoreCase(role)) {
            out.print("{\"success\":false,\"message\":\"Only counsellors can upload prescriptions\"}");
            return;
        }

        int counsellorId = (int) session.getAttribute("userId");

        try {
            // Form fields
            int    patientId      = Integer.parseInt(request.getParameter("patientId"));
            int    appointmentId  = Integer.parseInt(getOrDefault(request.getParameter("appointmentId"), "0"));
            String medication     = request.getParameter("medication");
            String dosage         = request.getParameter("dosage");
            String frequency      = request.getParameter("frequency");
            String duration       = request.getParameter("duration");
            String instructions   = request.getParameter("instructions");
            String prescribedDate = request.getParameter("prescribedDate");

            // File upload (optional)
            String fileName = null;
            String filePath = null;

            Part filePart = request.getPart("prescriptionFile");
            if (filePart != null && filePart.getSize() > 0) {
                String originalName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
                String ext          = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : "";
                fileName = "pres_" + System.currentTimeMillis() + ext;

                // Save to uploads/prescriptions/ inside the webapp
                String uploadDir = getServletContext().getRealPath("") + File.separator + "uploads" + File.separator + "prescriptions";
                Files.createDirectories(Paths.get(uploadDir));
                filePart.write(uploadDir + File.separator + fileName);
                filePath = "uploads/prescriptions/" + fileName;
            }

            // Insert into DB
            String sql = "INSERT INTO prescriptions " +
                "(patient_id, counsellor_id, appointment_id, medication, dosage, frequency, duration, instructions, prescribed_date, file_name, file_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt   (1, patientId);
                ps.setInt   (2, counsellorId);
                ps.setObject(3, appointmentId == 0 ? null : appointmentId);
                ps.setString(4, medication);
                ps.setString(5, dosage);
                ps.setString(6, frequency);
                ps.setString(7, duration);
                ps.setString(8, instructions);
                ps.setString(9, prescribedDate);
                ps.setString(10, fileName);
                ps.setString(11, filePath);

                ps.executeUpdate();
                out.print("{\"success\":true,\"message\":\"Prescription uploaded successfully\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"message\":\"Error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ── Patient: get own prescriptions ────────────────────────────────────────
    private String getPatientPrescriptions(int patientId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT p.*, u.name AS counsellor_name " +
            "FROM prescriptions p " +
            "JOIN users u ON p.counsellor_id = u.id " +
            "WHERE p.patient_id = ? " +
            "ORDER BY p.prescribed_date DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToMap(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"prescriptions\":" + listToJson(list) + "}";
    }

    // ── Counsellor: get prescriptions they uploaded ───────────────────────────
    private String getCounsellorPrescriptions(int counsellorId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT p.*, u.name AS patient_name " +
            "FROM prescriptions p " +
            "JOIN users u ON p.patient_id = u.id " +
            "WHERE p.counsellor_id = ? " +
            "ORDER BY p.prescribed_date DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, counsellorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rowToMap(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"prescriptions\":" + listToJson(list) + "}";
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object val = rs.getObject(i);
            map.put(meta.getColumnLabel(i), val != null ? val.toString() : "");
        }
        return map;
    }

    private String getOrDefault(String val, String def) {
        return (val == null || val.trim().isEmpty()) ? def : val;
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────
    private String listToJson(List<Map<String, Object>> list) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            json.append(mapToJson(list.get(i)));
        }
        return json.append("]").toString();
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (count++ > 0) json.append(",");
            json.append("\"").append(escapeJson(e.getKey())).append("\":\"")
                .append(escapeJson(String.valueOf(e.getValue()))).append("\"");
        }
        return json.append("}").toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}