package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
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

@WebServlet("/healthportal/counsellor-appointments")
public class CounselorDashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        // Check if user is logged in
        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        // Check if user has counsellor role
        String role = (String) session.getAttribute("userRole");
        if (!"counsellor".equals(role)) {
            out.print("{\"success\":false,\"message\":\"Access denied\"}");
            return;
        }

        int counsellorId = (int) session.getAttribute("userId");
        String doctorFilter = request.getParameter("doctor");

        out.print(getAppointments(counsellorId, doctorFilter));
    }

    /**
     * Fetch appointments from database
     * If doctorFilter is provided, filter by doctor name
     */
    private String getAppointments(int counsellorId, String doctorFilter) {
        List<Map<String, Object>> appointments = new ArrayList<>();
        
        // SQL query - assumes your database structure
        // Adjust table and column names according to your actual schema
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, p.name as patient_name, d.name as doctor_name, " +
            "a.appointment_date, a.appointment_time, a.reason, a.type, a.status, a.notes " +
            "FROM appointments a " +
            "JOIN users p ON a.patient_id = p.id " +
            "JOIN doctors d ON a.doctor_id = d.id " +
            "WHERE a.counsellor_id = ?"
        );

        List<Object> params = new ArrayList<>();
        params.add(counsellorId);

        if (doctorFilter != null && !doctorFilter.trim().isEmpty()) {
            sql.append(" AND d.name LIKE ?");
            params.add("%" + doctorFilter + "%");
        }

        sql.append(" ORDER BY a.appointment_date DESC, a.appointment_time DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            // Set parameters
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            ResultSet rs = pstmt.executeQuery();
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a");

            while (rs.next()) {
                Map<String, Object> app = new HashMap<>();
                app.put("id", rs.getInt("id"));
                app.put("patient", rs.getString("patient_name"));
                app.put("doctor", rs.getString("doctor_name"));
                
                java.sql.Date date = rs.getDate("appointment_date");
                app.put("date", date != null ? date.toString() : "");
                
                java.sql.Time time = rs.getTime("appointment_time");
                app.put("time", time != null ? timeFormat.format(time) : "");
                
                app.put("reason", rs.getString("reason") != null ? rs.getString("reason") : "");
                app.put("type", rs.getString("type") != null ? rs.getString("type") : "General");
                app.put("status", rs.getString("status") != null ? rs.getString("status") : "pending");
                app.put("notes", rs.getString("notes") != null ? rs.getString("notes") : "");
                
                appointments.add(app);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + e.getMessage() + "\"}";
        }

        return "{\"success\":true,\"appointments\":" + convertToJson(appointments) + "}";
    }

    /**
     * Convert List of Maps to JSON array
     */
    private String convertToJson(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            json.append(mapToJson(list.get(i)));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Convert a Map to JSON object
     */
    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (count++ > 0) json.append(",");
            
            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value == null) {
                json.append("null");
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }

    /**
     * Escape special characters for JSON
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("/", "\\/");
    }
}