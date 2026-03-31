package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import com.healthcare.util.DBConnection;

@WebServlet("/CounsellorProfileServlet")
public class CounsellorProfileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // GET: Load full profile (for dashboard and profile update page)
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        String role = (String) session.getAttribute("userRole");
        if (role == null || !"counsellor".equalsIgnoreCase(role.trim())) {
            out.print("{\"success\":false,\"message\":\"Access denied\"}");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        // Query all profile fields from users table
        String sql = "SELECT name, email, phone, registration_number, specialty, consultation_fee, " +
                     "experience_years, languages, bio, degrees, certifications, memberships, " +
                     "previous_job, organization FROM users WHERE id = ? AND role = 'counsellor'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Build JSON object with all fields
                StringBuilder json = new StringBuilder("{");
                json.append("\"success\":true,");
                json.append("\"name\":\"").append(escapeJson(rs.getString("name"))).append("\",");
                json.append("\"email\":\"").append(escapeJson(rs.getString("email"))).append("\",");
                json.append("\"phone\":\"").append(escapeJson(rs.getString("phone"))).append("\",");
                json.append("\"registration_number\":\"").append(escapeJson(rs.getString("registration_number"))).append("\",");
                json.append("\"specialty\":\"").append(escapeJson(rs.getString("specialty"))).append("\",");
                json.append("\"fee\":").append(rs.getDouble("consultation_fee")).append(",");
                json.append("\"experience_years\":").append(rs.getInt("experience_years")).append(",");
                json.append("\"languages\":\"").append(escapeJson(rs.getString("languages"))).append("\",");
                json.append("\"bio\":\"").append(escapeJson(rs.getString("bio"))).append("\",");
                json.append("\"degrees\":\"").append(escapeJson(rs.getString("degrees"))).append("\",");
                json.append("\"certifications\":\"").append(escapeJson(rs.getString("certifications"))).append("\",");
                json.append("\"memberships\":\"").append(escapeJson(rs.getString("memberships"))).append("\",");
                json.append("\"previous_job\":\"").append(escapeJson(rs.getString("previous_job"))).append("\",");
                json.append("\"organization\":\"").append(escapeJson(rs.getString("organization"))).append("\"");

                // Add avatar (initials) for dashboard
                String name = rs.getString("name");
                String avatar = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "C";
                json.append(",\"avatar\":\"").append(escapeJson(avatar)).append("\"");
                json.append(",\"role\":\"Counsellor\"");
                json.append("}");

                out.print(json.toString());
            } else {
                out.print("{\"success\":false,\"message\":\"Counsellor not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"message\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // POST: Update profile (for profile update page)
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        String role = (String) session.getAttribute("userRole");
        if (role == null || !"counsellor".equalsIgnoreCase(role.trim())) {
            out.print("{\"success\":false,\"message\":\"Access denied\"}");
            return;
        }

        int userId = (int) session.getAttribute("userId");

        // Retrieve all parameters from the form
        String name = request.getParameter("name");
        String phone = request.getParameter("phone");
        String registration_number = request.getParameter("registration_number");
        String specialty = request.getParameter("specialty");
        String feeStr = request.getParameter("fee");
        String experienceYearsStr = request.getParameter("experience_years");
        String languages = request.getParameter("languages");
        String bio = request.getParameter("bio");
        String degrees = request.getParameter("degrees");
        String certifications = request.getParameter("certifications");
        String memberships = request.getParameter("memberships");
        String previous_job = request.getParameter("previous_job");
        String organization = request.getParameter("organization");

        // Basic validation
        if (name == null || name.trim().isEmpty()) {
            out.print("{\"success\":false,\"message\":\"Name is required\"}");
            return;
        }

        double fee = 0.0;
        if (feeStr != null && !feeStr.isEmpty()) {
            try {
                fee = Double.parseDouble(feeStr);
            } catch (NumberFormatException e) {
                fee = 0.0;
            }
        }

        int experienceYears = 0;
        if (experienceYearsStr != null && !experienceYearsStr.isEmpty()) {
            try {
                experienceYears = Integer.parseInt(experienceYearsStr);
            } catch (NumberFormatException e) {
                experienceYears = 0;
            }
        }

        // Update query
        String sql = "UPDATE users SET name = ?, phone = ?, registration_number = ?, specialty = ?, consultation_fee = ?, " +
                     "experience_years = ?, languages = ?, bio = ?, degrees = ?, certifications = ?, memberships = ?, " +
                     "previous_job = ?, organization = ? WHERE id = ? AND role = 'counsellor'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, registration_number);
            ps.setString(4, specialty);
            ps.setDouble(5, fee);
            ps.setInt(6, experienceYears);
            ps.setString(7, languages);
            ps.setString(8, bio);
            ps.setString(9, degrees);
            ps.setString(10, certifications);
            ps.setString(11, memberships);
            ps.setString(12, previous_job);
            ps.setString(13, organization);
            ps.setInt(14, userId);

            int rows = ps.executeUpdate();

            if (rows > 0) {
                // Update session name if changed
                session.setAttribute("userName", name);
                out.print("{\"success\":true}");
            } else {
                out.print("{\"success\":false,\"message\":\"No changes made\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"message\":\"Database error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}