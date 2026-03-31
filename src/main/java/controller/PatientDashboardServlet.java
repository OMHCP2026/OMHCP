package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import com.healthcare.util.DBConnection;

@WebServlet("/PatientDashboardServlet")
public class PatientDashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        // 🔐 Login check
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect("login.html");
            return;
        }

        // 🔐 Role check
        String role = (String) session.getAttribute("userRole");
        if (!"patient".equals(role)) {
            response.sendRedirect("login.html");
            return;
        }

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        int    patientId = (int) session.getAttribute("userId");
        String action    = request.getParameter("action");

        if ("getUserInfo".equals(action)) {
            out.print(getUserInfo(patientId));

        } else if ("getAppointments".equals(action)) {
            // ✅ Called by loadUpcomingAppointments() in JS
            out.print("{\"success\":true,\"appointments\":" + getAppointmentsJson(patientId) + "}");

        } else {
            out.print(getDashboardData(patientId));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // GET USER INFO
    // ════════════════════════════════════════════════════════════════
    private String getUserInfo(int patientId) {
        StringBuilder json = new StringBuilder();
        String sql = "SELECT id, name, email FROM users WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                json.append("{\"success\":true,\"user\":{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"name\":\"").append(escapeJson(rs.getString("name"))).append("\",");
                json.append("\"email\":\"").append(escapeJson(rs.getString("email"))).append("\",");
                json.append("\"role\":\"patient\"");
                json.append("}}");
            } else {
                json.append("{\"success\":false,\"message\":\"User not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            json.append("{\"success\":false,\"message\":\"Database error\"}");
        }

        return json.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // GET FULL DASHBOARD DATA (stats + appointments)
    // ════════════════════════════════════════════════════════════════
    private String getDashboardData(int patientId) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,");

        String sql =
            "SELECT " +
            "(SELECT COUNT(*) FROM appointments WHERE patient_id = ?) AS total, " +
            "(SELECT COUNT(*) FROM appointments WHERE patient_id = ? AND status='pending') AS pending, " +
            "(SELECT COUNT(*) FROM appointments WHERE patient_id = ? AND status='confirmed' AND appointment_date >= CURDATE()) AS upcoming, " +
            "(SELECT COUNT(*) FROM appointments WHERE patient_id = ? AND status='completed') AS completed, " +
            "(SELECT COUNT(*) FROM prescriptions WHERE patient_id = ?) AS prescriptions";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 1; i <= 5; i++) ps.setInt(i, patientId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                json.append("\"stats\":{");
                json.append("\"totalAppointments\":").append(rs.getInt("total")).append(",");
                json.append("\"pendingAppointments\":").append(rs.getInt("pending")).append(",");
                json.append("\"upcomingAppointments\":").append(rs.getInt("upcoming")).append(",");
                json.append("\"completedAppointments\":").append(rs.getInt("completed")).append(",");
                json.append("\"totalPrescriptions\":").append(rs.getInt("prescriptions"));
                json.append("},");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        json.append("\"appointments\":").append(getAppointmentsJson(patientId));
        json.append("}");
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // GET APPOINTMENTS JSON
    // ✅ FIX: Added counsellor_id, mode, specialty
    //    These are needed by the "View Doctor" modal in patient dashboard
    // ════════════════════════════════════════════════════════════════
    private String getAppointmentsJson(int patientId) {
        StringBuilder json  = new StringBuilder("[");
        boolean       first = true;

        // ✅ FIXED SQL: now fetches counsellor_id, mode, specialty
        // Uses LEFT JOIN on counsellor_profiles for specialty
        // Falls back to 'Counsellor' if specialty not set
        // Falls back to 'video' if mode column doesn't exist
        String sql =
            "SELECT a.id, " +
            "       a.counsellor_id, " +
            "       u.name  AS doctor_name, " +
            "       a.appointment_date, " +
            "       a.appointment_time, " +
            "       a.status, " +
            "       a.reason, " +
            "       COALESCE(a.mode, 'video') AS mode, " +
            "       COALESCE(cp.specialty, 'Counsellor') AS specialty " +
            "FROM appointments a " +
            "JOIN  users u  ON a.counsellor_id = u.id " +
            "LEFT JOIN counsellor_profiles cp ON cp.user_id = a.counsellor_id " +
            "WHERE a.patient_id = ? " +
            "  AND a.appointment_date >= CURDATE() " +
            "ORDER BY a.appointment_date ASC, a.appointment_time ASC " +
            "LIMIT 5";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                if (!first) json.append(",");

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");

                // ✅ counsellorId — needed for View Doctor modal
                json.append("\"counsellorId\":").append(rs.getInt("counsellor_id")).append(",");

                json.append("\"doctorName\":\"").append(escapeJson(rs.getString("doctor_name"))).append("\",");
                json.append("\"date\":\"").append(rs.getDate("appointment_date")).append("\",");
                json.append("\"time\":\"").append(rs.getString("appointment_time") != null
                        ? rs.getString("appointment_time").substring(0, 5) : "").append("\",");
                json.append("\"status\":\"").append(escapeJson(rs.getString("status"))).append("\",");
                json.append("\"reason\":\"").append(escapeJson(rs.getString("reason") != null
                        ? rs.getString("reason") : "")).append("\",");

                // ✅ mode — fixes "undefined session" text
                json.append("\"mode\":\"").append(escapeJson(rs.getString("mode"))).append("\",");

                // ✅ specialty — shows doctor's specialty on card
                json.append("\"specialty\":\"").append(escapeJson(rs.getString("specialty"))).append("\"");

                json.append("}");
                first = false;
            }

        } catch (SQLException e) {
            e.printStackTrace();

            // ── Fallback: if counsellor_profiles table doesn't exist,
            //    retry without the LEFT JOIN ──
            return getAppointmentsJsonFallback(patientId);
        }

        json.append("]");
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // FALLBACK — if counsellor_profiles table doesn't exist yet
    // ════════════════════════════════════════════════════════════════
    private String getAppointmentsJsonFallback(int patientId) {
        StringBuilder json  = new StringBuilder("[");
        boolean       first = true;

        String sql =
            "SELECT a.id, a.counsellor_id, u.name AS doctor_name, " +
            "       a.appointment_date, a.appointment_time, a.status, a.reason " +
            "FROM appointments a " +
            "JOIN users u ON a.counsellor_id = u.id " +
            "WHERE a.patient_id = ? " +
            "  AND a.appointment_date >= CURDATE() " +
            "ORDER BY a.appointment_date ASC, a.appointment_time ASC " +
            "LIMIT 5";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                if (!first) json.append(",");

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"counsellorId\":").append(rs.getInt("counsellor_id")).append(",");
                json.append("\"doctorName\":\"").append(escapeJson(rs.getString("doctor_name"))).append("\",");
                json.append("\"date\":\"").append(rs.getDate("appointment_date")).append("\",");
                json.append("\"time\":\"").append(rs.getString("appointment_time") != null
                        ? rs.getString("appointment_time").substring(0, 5) : "").append("\",");
                json.append("\"status\":\"").append(escapeJson(rs.getString("status"))).append("\",");
                json.append("\"reason\":\"").append(escapeJson(rs.getString("reason") != null
                        ? rs.getString("reason") : "")).append("\",");
                json.append("\"mode\":\"video\",");
                json.append("\"specialty\":\"Counsellor\"");
                json.append("}");

                first = false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        json.append("]");
        return json.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // HELPER
    // ════════════════════════════════════════════════════════════════
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}