package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.healthcare.util.DBConnection;

@WebServlet("/AppointmentServlet")
public class AppointmentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // ════════════════════════════════════════════════════════════════
    //  GET
    // ════════════════════════════════════════════════════════════════
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Please login first\"}");
            return;
        }

        String role   = (String) session.getAttribute("userRole");
        if (role != null) role = role.trim();
        int    userId = (int) session.getAttribute("userId");
        String action = request.getParameter("action");

        // ── ADMIN ────────────────────────────────────────────────
        if ("admin".equalsIgnoreCase(role)) {
            if ("getRecent".equals(action)) {
                out.print(getRecentAppointments());
            } else {
                out.print(getAllAppointmentsAdmin(request));
            }
            return;
        }

        // ── PATIENT ──────────────────────────────────────────────
        if ("getMyAppointments".equals(action)) {
            if (!"patient".equalsIgnoreCase(role)) {
                out.print("{\"success\":false,\"message\":\"Access denied\"}"); return;
            }
            out.print(getPatientAppointments(userId));

        } else if ("getAll".equals(action)) {
            if (!"counsellor".equalsIgnoreCase(role)) {
                out.print("{\"success\":false,\"message\":\"Access denied\"}"); return;
            }
            out.print(getAllAppointments());

        } else if ("getNotifications".equals(action)) {
            if (!"patient".equalsIgnoreCase(role)) {
                out.print("{\"success\":false,\"message\":\"Access denied\"}"); return;
            }
            out.print(getUnreadNotifications(userId));

        } else if ("markRead".equals(action)) {
            String nid = request.getParameter("notifId");
            if (nid != null) markNotificationRead(Integer.parseInt(nid));
            out.print("{\"success\":true}");

        } else {
            if (!"counsellor".equalsIgnoreCase(role)) {
                out.print("{\"success\":false,\"message\":\"Access denied\"}"); return;
            }
            out.print(getCounsellorAppointments(userId));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST
    // ════════════════════════════════════════════════════════════════
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Please login first\"}"); return;
        }

        String role   = (String) session.getAttribute("userRole");
        if (role != null) role = role.trim();
        int    userId = (int) session.getAttribute("userId");
        String action = request.getParameter("action");

        // updateStatus — counsellor OR admin
        if ("updateStatus".equals(action)) {
            if (!"counsellor".equalsIgnoreCase(role) && !"admin".equalsIgnoreCase(role)) {
                out.print("{\"success\":false,\"message\":\"Access denied\"}"); return;
            }
            String idStr     = request.getParameter("appointmentId");
            String newStatus = request.getParameter("status");

            if (idStr == null || newStatus == null) {
                out.print("{\"success\":false,\"message\":\"Missing parameters\"}"); return;
            }
            if (!newStatus.equals("confirmed") && !newStatus.equals("completed")
                    && !newStatus.equals("cancelled") && !newStatus.equals("pending")) {
                out.print("{\"success\":false,\"message\":\"Invalid status value\"}"); return;
            }

            // Admin always skips ownership check
            boolean skipCheck = "admin".equalsIgnoreCase(role)
                    || "all".equals(request.getParameter("view"));

            out.print(updateStatus(Integer.parseInt(idStr), userId, newStatus, skipCheck));

        } else if ("cancel".equals(action)) {
            String idStr = request.getParameter("appointmentId");
            if (idStr == null) {
                out.print("{\"success\":false,\"message\":\"Missing appointment ID\"}"); return;
            }
            out.print(cancelAppointment(Integer.parseInt(idStr), userId, role));

        } else {
            out.print("{\"success\":false,\"message\":\"Unknown action\"}");
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ADMIN – GET RECENT (last 5, for dashboard widget)
    // ════════════════════════════════════════════════════════════════
    private String getRecentAppointments() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT a.id, a.patient_id, " +
            "       u1.name AS patient_name, " +
            "       u2.name AS counsellor_name, " +
            "       a.appointment_date, a.appointment_time, " +
            "       a.reason, a.status " +
            "FROM appointments a " +
            "JOIN users u1 ON a.patient_id    = u1.id " +
            "JOIN users u2 ON a.counsellor_id = u2.id " +
            "ORDER BY a.appointment_date DESC, a.appointment_time DESC " +
            "LIMIT 5";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(appointmentRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"appointments\":" + listToJson(list) + "}";
    }

    // ════════════════════════════════════════════════════════════════
    //  ADMIN – GET ALL with optional search / status / date filters
    // ════════════════════════════════════════════════════════════════
    private String getAllAppointmentsAdmin(HttpServletRequest req) {
        String search = req.getParameter("search");
        String status = req.getParameter("status");
        String date   = req.getParameter("date");

        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.patient_id, " +
            "       u1.name AS patient_name, " +
            "       u2.name AS counsellor_name, " +
            "       a.appointment_date, a.appointment_time, " +
            "       a.reason, a.status " +
            "FROM appointments a " +
            "JOIN users u1 ON a.patient_id    = u1.id " +
            "JOIN users u2 ON a.counsellor_id = u2.id " +
            "WHERE 1=1 "
        );

        if (search != null && !search.trim().isEmpty())
            sql.append("AND (u1.name LIKE ? OR u2.name LIKE ?) ");
        if (status != null && !status.trim().isEmpty())
            sql.append("AND a.status = ? ");
        if (date != null && !date.trim().isEmpty())
            sql.append("AND DATE(a.appointment_date) = ? ");

        sql.append("ORDER BY a.appointment_date DESC, a.appointment_time DESC");

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            if (search != null && !search.trim().isEmpty()) {
                String like = "%" + search.trim() + "%";
                ps.setString(idx++, like);
                ps.setString(idx++, like);
            }
            if (status != null && !status.trim().isEmpty())
                ps.setString(idx++, status.trim());
            if (date != null && !date.trim().isEmpty())
                ps.setString(idx++, date.trim());

            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(appointmentRow(rs));

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"appointments\":" + listToJson(list) + "}";
    }

    // ════════════════════════════════════════════════════════════════
    //  UPDATE STATUS + NOTIFICATION
    // ════════════════════════════════════════════════════════════════
    private String updateStatus(int appointmentId, int counsellorId,
                                String newStatus, boolean skipCounsellorCheck) {
        String updateSql = skipCounsellorCheck
            ? "UPDATE appointments SET status=? WHERE id=?"
            : "UPDATE appointments SET status=? WHERE id=? AND counsellor_id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {

            ps.setString(1, newStatus);
            ps.setInt(2, appointmentId);
            if (!skipCounsellorCheck) ps.setInt(3, counsellorId);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                int actualCounsellorId = getAppointmentCounsellorId(conn, appointmentId);
                insertNotification(conn, appointmentId, actualCounsellorId, newStatus);

                String msg = newStatus.equals("confirmed")
                    ? "Appointment confirmed! Patient has been notified."
                    : newStatus.equals("completed")
                        ? "Appointment marked as completed!"
                        : "Appointment cancelled. Patient has been notified.";
                return "{\"success\":true,\"message\":\"" + msg + "\"}";
            } else {
                System.out.println("DEBUG updateStatus: rows=0"
                    + " appointmentId=" + appointmentId
                    + " counsellorId="  + counsellorId
                    + " skipCheck="     + skipCounsellorCheck
                    + " newStatus="     + newStatus);
                return "{\"success\":false,\"message\":\"Appointment not found or access denied\"}";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private int getAppointmentCounsellorId(Connection conn, int appointmentId) {
        String sql = "SELECT counsellor_id FROM appointments WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("counsellor_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  INSERT NOTIFICATION FOR PATIENT
    // ════════════════════════════════════════════════════════════════
    private void insertNotification(Connection conn, int appointmentId,
                                    int counsellorId, String newStatus) {
        String fetchSql =
            "SELECT a.patient_id, u.name AS counsellor_name, " +
            "       a.appointment_date, a.appointment_time " +
            "FROM appointments a " +
            "JOIN users u ON u.id = a.counsellor_id " +
            "WHERE a.id = ? AND a.counsellor_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(fetchSql)) {
            ps.setInt(1, appointmentId);
            ps.setInt(2, counsellorId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int    patientId      = rs.getInt("patient_id");
                String counsellorName = rs.getString("counsellor_name");
                String date           = rs.getDate("appointment_date").toString();
                String time           = rs.getTime("appointment_time").toString().substring(0, 5);

                String title, message;
                if ("confirmed".equals(newStatus)) {
                    title   = "Appointment Confirmed!";
                    message = "Great news! Your appointment with " + counsellorName +
                              " on " + date + " at " + time +
                              " has been confirmed. Please be available on time.";
                } else if ("completed".equals(newStatus)) {
                    title   = "Appointment Completed";
                    message = "Your appointment with " + counsellorName +
                              " on " + date + " at " + time +
                              " has been marked as completed. We hope your session went well!";
                } else {
                    title   = "Appointment Cancelled";
                    message = "Your appointment with " + counsellorName +
                              " on " + date + " at " + time +
                              " has been cancelled. Please book a new slot.";
                }

                String insertSql =
                    "INSERT INTO notifications (user_id, title, message, type, is_read, appointment_id) " +
                    "VALUES (?, ?, ?, 'appointment', 0, ?)";

                try (PreparedStatement ps2 = conn.prepareStatement(insertSql)) {
                    ps2.setInt(1, patientId);
                    ps2.setString(2, title);
                    ps2.setString(3, message);
                    ps2.setInt(4, appointmentId);
                    ps2.executeUpdate();
                }
            } else {
                System.out.println("DEBUG insertNotification: no row found"
                    + " appointmentId=" + appointmentId
                    + " counsellorId="  + counsellorId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET PATIENT APPOINTMENTS
    // ════════════════════════════════════════════════════════════════
    private String getPatientAppointments(int patientId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT a.id, a.patient_id, u.name AS doctor_name, " +
            "       a.appointment_date, a.appointment_time, a.reason, a.status " +
            "FROM appointments a " +
            "JOIN users u ON a.counsellor_id = u.id " +
            "WHERE a.patient_id = ? " +
            "ORDER BY a.appointment_date DESC, a.appointment_time DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> app = new HashMap<>();
                app.put("id",         rs.getInt("id"));
                app.put("patientId",  rs.getInt("patient_id"));
                app.put("doctorName", rs.getString("doctor_name"));
                app.put("date",       rs.getDate("appointment_date").toString());
                app.put("time",       rs.getTime("appointment_time").toString().substring(0, 5));
                app.put("reason",     rs.getString("reason") != null ? rs.getString("reason") : "");
                app.put("status",     rs.getString("status"));
                list.add(app);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"appointments\":" + listToJson(list) + "}";
    }

    // ════════════════════════════════════════════════════════════════
    //  GET COUNSELLOR APPOINTMENTS (own)
    // ════════════════════════════════════════════════════════════════
    private String getCounsellorAppointments(int counsellorId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT a.id, a.patient_id, u.name AS patient_name, uc.name AS counsellor_name, " +
            "       a.appointment_date, a.appointment_time, a.reason, a.status " +
            "FROM appointments a " +
            "JOIN users u  ON a.patient_id    = u.id " +
            "JOIN users uc ON a.counsellor_id = uc.id " +
            "WHERE a.counsellor_id = ? " +
            "ORDER BY a.appointment_date DESC, a.appointment_time DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, counsellorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(appointmentRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"appointments\":" + listToJson(list) + "}";
    }

    // ════════════════════════════════════════════════════════════════
    //  GET ALL APPOINTMENTS (counsellor doctor-tab)
    // ════════════════════════════════════════════════════════════════
    private String getAllAppointments() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT a.id, a.patient_id, u.name AS patient_name, uc.name AS counsellor_name, " +
            "       a.appointment_date, a.appointment_time, a.reason, a.status " +
            "FROM appointments a " +
            "JOIN users u  ON a.patient_id    = u.id " +
            "JOIN users uc ON a.counsellor_id = uc.id " +
            "ORDER BY a.appointment_date DESC, a.appointment_time DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(appointmentRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"appointments\":" + listToJson(list) + "}";
    }

    // ════════════════════════════════════════════════════════════════
    //  CANCEL APPOINTMENT
    // ════════════════════════════════════════════════════════════════
    private String cancelAppointment(int appointmentId, int userId, String role) {
        String sql;
        if ("admin".equalsIgnoreCase(role)) {
            sql = "UPDATE appointments SET status='cancelled' WHERE id=?";
        } else if ("patient".equalsIgnoreCase(role)) {
            sql = "UPDATE appointments SET status='cancelled' WHERE id=? AND patient_id=?";
        } else {
            sql = "UPDATE appointments SET status='cancelled' WHERE id=? AND counsellor_id=?";
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, appointmentId);
            if (!"admin".equalsIgnoreCase(role)) ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            return rows > 0
                ? "{\"success\":true,\"message\":\"Appointment cancelled\"}"
                : "{\"success\":false,\"message\":\"Not found or already cancelled\"}";
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET UNREAD NOTIFICATIONS
    // ════════════════════════════════════════════════════════════════
    private String getUnreadNotifications(int userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT id, title, message, type, appointment_id, created_at " +
            "FROM notifications " +
            "WHERE user_id = ? AND is_read = 0 " +
            "ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> n = new HashMap<>();
                n.put("id",            rs.getInt("id"));
                n.put("title",         rs.getString("title"));
                n.put("message",       rs.getString("message"));
                n.put("type",          rs.getString("type"));
                n.put("appointmentId", rs.getInt("appointment_id"));
                n.put("createdAt",     rs.getTimestamp("created_at").toString());
                list.add(n);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
        return "{\"success\":true,\"notifications\":" + listToJson(list) + "}";
    }

    private void markNotificationRead(int notifId) {
        String sql = "UPDATE notifications SET is_read=1 WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notifId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════
    private Map<String, Object> appointmentRow(ResultSet rs) throws SQLException {
        Map<String, Object> app = new HashMap<>();
        app.put("id",             rs.getInt("id"));
        app.put("patientId",      rs.getInt("patient_id"));
        app.put("patient",        rs.getString("patient_name"));
        app.put("counsellorName", rs.getString("counsellor_name"));
        app.put("date",           rs.getDate("appointment_date").toString());
        app.put("time",           rs.getTime("appointment_time").toString().substring(0, 5));
        app.put("reason",         rs.getString("reason") != null ? rs.getString("reason") : "");
        app.put("status",         rs.getString("status"));
        return app;
    }

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
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (count++ > 0) json.append(",");
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val == null)
                json.append("null");
            else if (val instanceof String)
                json.append("\"").append(escapeJson((String) val)).append("\"");
            else if (val instanceof Number || val instanceof Boolean)
                json.append(val);
            else
                json.append("\"").append(escapeJson(val.toString())).append("\"");
        }
        return json.append("}").toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}