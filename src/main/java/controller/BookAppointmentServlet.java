package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import com.healthcare.util.DBConnection;

@WebServlet("/BookAppointmentServlet")
public class BookAppointmentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(BookAppointmentServlet.class.getName());

    private static final String[] ALL_SLOTS = {"09:00", "10:00", "11:00", "12:00", "14:00", "15:00", "16:00", "17:00"};

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setJsonResponse(response);
        String action = request.getParameter("action");
        LOGGER.info("doGet action: " + action);

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            writeJson(response, errorJson("Please login first"));
            return;
        }

        String role = (String) session.getAttribute("userRole");
        if (!"patient".equalsIgnoreCase(role)) {
            writeJson(response, errorJson("Access denied. Only patients can book appointments."));
            return;
        }

        String jsonResponse;
        if ("getCounselors".equals(action)) {
            jsonResponse = getAllowedCounselors();
        } else if ("getSlots".equals(action)) {
            jsonResponse = getAvailableSlots(
                request.getParameter("counselorId"),
                request.getParameter("date")
            );
        } else {
            LOGGER.warning("Unknown GET action: " + action);
            jsonResponse = errorJson("Invalid action. Please refresh and try again.");
        }
        writeJson(response, jsonResponse);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setJsonResponse(response);
        String action = request.getParameter("action");
        LOGGER.info("doPost action: " + action);

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            writeJson(response, errorJson("Please login first"));
            return;
        }

        String role = (String) session.getAttribute("userRole");
        if (!"patient".equalsIgnoreCase(role)) {
            writeJson(response, errorJson("Access denied. Only patients can book appointments."));
            return;
        }

        int patientId = (int) session.getAttribute("userId");

        String jsonResponse;
        if ("bookAppointment".equals(action)) {
            jsonResponse = bookAppointment(request, patientId);
        } else {
            LOGGER.warning("Unknown POST action: " + action);
            jsonResponse = errorJson("Invalid action. Please try again.");
        }
        writeJson(response, jsonResponse);
    }

    private void writeJson(HttpServletResponse response, String json) throws IOException {
        PrintWriter out = response.getWriter();
        out.print(json);
        out.flush();
    }

    // ───────────────────────────────────────────────
    //  Get allowed counselors (UPDATED with all fields)
    // ───────────────────────────────────────────────
    private String getAllowedCounselors() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT id, name, specialty, experience_years, consultation_fee, " +
            "qualification, languages, about, registration_number, degrees, college, " +
            "certifications, memberships, rating, total_reviews, profile_pic, consultation_mode " +
            "FROM users WHERE role = 'counsellor' AND is_allowed = TRUE ORDER BY name";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> c = new HashMap<>();
                c.put("id",                  rs.getInt("id"));
                c.put("name",                rs.getString("name"));
                c.put("specialty",            rs.getString("specialty") != null ? rs.getString("specialty") : "General Counselor");
                c.put("experience",           rs.getInt("experience_years"));
                c.put("fee",                  rs.getDouble("consultation_fee"));
                c.put("qualification",        rs.getString("qualification"));
                c.put("languages",            rs.getString("languages"));
                c.put("about",                rs.getString("about"));
                c.put("registration_number",  rs.getString("registration_number"));
                c.put("degrees",              rs.getString("degrees"));
                c.put("college",              rs.getString("college"));
                c.put("certifications",       rs.getString("certifications"));
                c.put("memberships",          rs.getString("memberships"));
                c.put("rating",               rs.getDouble("rating"));
                c.put("total_reviews",        rs.getInt("total_reviews"));
                c.put("profile_pic",          rs.getString("profile_pic"));
                c.put("consultation_mode",    rs.getString("consultation_mode"));
                list.add(c);
            }
            return successJson("counselors", list);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getAllowedCounselors DB error", e);
            return errorJson("Unable to load counselors. Please try again later.");
        }
    }

    // ───────────────────────────────────────────────
    //  Get available slots (rejects today/past dates)
    // ───────────────────────────────────────────────
    private String getAvailableSlots(String counselorId, String date) {
        if (isBlank(counselorId) || isBlank(date))
            return errorJson("Counselor ID and date are required");

        int cid;
        try {
            cid = Integer.parseInt(counselorId);
        } catch (NumberFormatException e) {
            return errorJson("Invalid counselor ID");
        }

        // Validate date – cannot be today or in the past
        try {
            LocalDate apptDate = LocalDate.parse(date);
            if (!apptDate.isAfter(LocalDate.now())) {
                return errorJson("Cannot view slots for today or past dates");
            }
        } catch (DateTimeParseException e) {
            return errorJson("Invalid date format");
        }

        if (!isCounselorAllowed(cid)) {
            return errorJson("Selected counselor is not available");
        }

        Set<String> booked = new HashSet<>();
        String sql =
            "SELECT appointment_time FROM appointments " +
            "WHERE counsellor_id = ? AND appointment_date = ? AND status != 'cancelled'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, cid);
            ps.setDate(2, java.sql.Date.valueOf(date));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Time t = rs.getTime("appointment_time");
                    if (t != null) {
                        String timeStr = t.toString();
                        booked.add(timeStr.length() > 5 ? timeStr.substring(0, 5) : timeStr);
                    }
                }
            }

            List<Map<String, Object>> slots = new ArrayList<>();
            for (String slot : ALL_SLOTS) {
                Map<String, Object> m = new HashMap<>();
                m.put("time", slot);
                m.put("available", !booked.contains(slot));
                slots.add(m);
            }
            return successJson("slots", slots);

        } catch (IllegalArgumentException e) {
            return errorJson("Invalid date format");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getAvailableSlots DB error", e);
            return errorJson("Unable to load slots. Please try again.");
        }
    }

    // ───────────────────────────────────────────────
    //  Book an appointment (rejects today/past dates)
    // ───────────────────────────────────────────────
    private String bookAppointment(HttpServletRequest request, int patientId) {
        String counselorId   = request.getParameter("counselorId");
        String date          = request.getParameter("date");
        String time          = request.getParameter("time");
        String reason        = request.getParameter("reason");
        String paymentMethod = request.getParameter("paymentMethod");
        if (isBlank(paymentMethod)) paymentMethod = "offline";

        if (isBlank(counselorId)) return errorJson("Please select a doctor");
        if (isBlank(date))        return errorJson("Please select a date");
        if (isBlank(time))        return errorJson("Please select a time");

        int cid;
        try {
            cid = Integer.parseInt(counselorId);
        } catch (NumberFormatException e) {
            return errorJson("Invalid counselor ID");
        }

        // Validate date – cannot be today or in the past
        try {
            LocalDate apptDate = LocalDate.parse(date);
            LocalDate today    = LocalDate.now();
            if (!apptDate.isAfter(today)) {
                return errorJson("Appointment date cannot be today or in the past");
            }
            if (apptDate.isAfter(today.plusDays(7))) {
                return errorJson("Can only book up to 7 days in advance");
            }
        } catch (DateTimeParseException e) {
            return errorJson("Invalid date format");
        }

        if (!time.matches("^([01]\\d|2[0-3]):[0-5]\\d$"))
            return errorJson("Invalid time format");

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            if (!isCounselorAllowed(cid)) {
                conn.rollback();
                return errorJson("Selected doctor is not available for booking");
            }

            if (isSlotBooked(conn, cid, date, time)) {
                conn.rollback();
                return errorJson("This time slot is already booked. Please choose another.");
            }

            double fee = getConsultationFee(conn, cid);

            String paymentStatus = ("fake".equals(paymentMethod) || "online".equals(paymentMethod))
                    ? "paid" : "pending";

            // ── STEP 1: Insert appointment first (no payment_id yet) ──
            LOGGER.info("Step 1: Inserting appointment for patient=" + patientId + " counselor=" + cid);
            String apptSql =
                "INSERT INTO appointments " +
                "(patient_id, counsellor_id, appointment_date, appointment_time, reason, status, payment_status) " +
                "VALUES (?, ?, ?, ?, ?, 'pending', ?)";

            int appointmentId = -1;
            try (PreparedStatement ps = conn.prepareStatement(apptSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, patientId);
                ps.setInt(2, cid);
                ps.setDate(3, java.sql.Date.valueOf(date));
                ps.setTime(4, java.sql.Time.valueOf(time + ":00"));
                ps.setString(5, isBlank(reason) ? "General Consultation" : reason);
                ps.setString(6, paymentStatus);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) appointmentId = keys.getInt(1);
                }
            }

            if (appointmentId == -1) {
                conn.rollback();
                return errorJson("Failed to create appointment record");
            }
            LOGGER.info("Step 1 done: appointmentId=" + appointmentId);

            // ── STEP 2: Insert payment with real appointment_id ──
            LOGGER.info("Step 2: Inserting payment record...");
            int paymentId = insertPayment(conn, patientId, fee, paymentMethod, appointmentId);
            if (paymentId == -1) {
                conn.rollback();
                return errorJson("Payment initialization failed");
            }
            LOGGER.info("Step 2 done: paymentId=" + paymentId);

            // ── STEP 3: Update appointment with payment_id ──
            LOGGER.info("Step 3: Linking payment to appointment...");
            String updateSql = "UPDATE appointments SET payment_id = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, paymentId);
                ps.setInt(2, appointmentId);
                ps.executeUpdate();
            }
            LOGGER.info("Step 3 done.");

            conn.commit();
            LOGGER.info("Booking SUCCESS: patient=" + patientId + " appt=" + appointmentId + " payment=" + paymentId);
            return "{\"success\":true,\"message\":\"Appointment booked successfully!\"}";

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
            LOGGER.log(Level.SEVERE, "SQL error during booking for patient " + patientId, e);
            return errorJson("SQL Error [" + e.getErrorCode() + "]: " + e.getMessage());

        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
            LOGGER.log(Level.SEVERE, "Unexpected error during booking", e);
            return errorJson("Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());

        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignore) {}
        }
    }

    // ───────────────────────────────────────────────
    //  Helpers
    // ───────────────────────────────────────────────
    private boolean isCounselorAllowed(int cid) {
        String sql = "SELECT is_allowed FROM users WHERE id = ? AND role = 'counsellor'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("is_allowed");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking counselor allowed", e);
            return false;
        }
    }

    private boolean isSlotBooked(Connection conn, int cid, String date, String time) throws SQLException {
        String sql =
            "SELECT id FROM appointments " +
            "WHERE counsellor_id = ? AND appointment_date = ? AND appointment_time = ? " +
            "AND status != 'cancelled' FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cid);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.setTime(3, java.sql.Time.valueOf(time + ":00"));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private double getConsultationFee(Connection conn, int cid) throws SQLException {
        String sql = "SELECT consultation_fee FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private int insertPayment(Connection conn, int patientId, double amount, String method, int appointmentId) throws SQLException {
        String status = "fake".equals(method) ? "completed" : "pending";
        String sql =
            "INSERT INTO payments (patient_id, amount, status, payment_method, created_at, appointment_id) " +
            "VALUES (?, ?, ?, ?, NOW(), ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patientId);
            ps.setDouble(2, amount);
            ps.setString(3, status);
            ps.setString(4, method);
            ps.setInt(5, appointmentId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ───────────────────────────────────────────────
    //  JSON utilities
    // ───────────────────────────────────────────────
    private void setJsonResponse(HttpServletResponse r) {
        r.setContentType("application/json");
        r.setCharacterEncoding("UTF-8");
    }

    private String errorJson(String message) {
        return "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}";
    }

    private String successJson(String key, List<Map<String, Object>> list) {
        return "{\"success\":true,\"" + key + "\":" + toJsonArray(list) + "}";
    }

    private String toJsonArray(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            int j = 0;
            for (Map.Entry<String, Object> e : list.get(i).entrySet()) {
                if (j++ > 0) sb.append(",");
                sb.append('"').append(escapeJson(e.getKey())).append("\":");
                Object val = e.getValue();
                if (val == null) {
                    sb.append("null");
                } else if (val instanceof String) {
                    sb.append('"').append(escapeJson((String) val)).append('"');
                } else if (val instanceof Number || val instanceof Boolean) {
                    sb.append(val);
                } else {
                    sb.append('"').append(escapeJson(val.toString())).append('"');
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}