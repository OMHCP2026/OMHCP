package controller;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/CounsellorServlet")
public class CounsellorServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String action = request.getParameter("action");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if ("getAll".equals(action)) {
            getAllCounsellors(response);
        } else {
            response.getWriter().write("{\"success\":false,\"message\":\"Unknown action\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String action = request.getParameter("action");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if ("approveWithDetails".equals(action)) {
            approveWithDetails(request, response);
        } else if ("updateStatus".equals(action)) {
            updateStatus(request, response);
        } else {
            response.getWriter().write("{\"success\":false,\"message\":\"Unknown action\"}");
        }
    }

    // ── GET ALL COUNSELLORS ────────────────────────────────────
    private void getAllCounsellors(HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();
        StringBuilder json = new StringBuilder();

        try (Connection conn = com.healthcare.util.DBConnection.getConnection()) {

            String sql = "SELECT id, name, email, contact, specialty, " +
                         "consultation_fee, status, qualification, experience_years " +
                         "FROM users WHERE role = 'counsellor' ORDER BY id DESC";

            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            json.append("{\"success\":true,\"counsellors\":[");
            boolean first = true;

            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":")              .append(rs.getInt("id"))                          .append(",");
                json.append("\"name\":\"")          .append(escape(rs.getString("name")))             .append("\",");
                json.append("\"email\":\"")         .append(escape(rs.getString("email")))            .append("\",");
                json.append("\"phone\":\"")         .append(escape(rs.getString("contact")))          .append("\",");
                json.append("\"specialty\":\"")     .append(escape(rs.getString("specialty")))        .append("\",");
                json.append("\"fee\":")             .append(rs.getDouble("consultation_fee"))         .append(",");
                json.append("\"status\":\"")        .append(escape(rs.getString("status")))           .append("\",");
                json.append("\"qualification\":\"") .append(escape(rs.getString("qualification")))    .append("\",");
                json.append("\"experience\":")      .append(rs.getInt("experience_years"));
                json.append("}");
            }

            json.append("]}");
            out.write(json.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            out.write("{\"success\":false,\"message\":\"DB error: " + escape(e.getMessage()) + "\"}");
        }
    }

    // ── APPROVE WITH DETAILS ───────────────────────────────────
    // Admin must fill specialty, experience, fee before approving
    private void approveWithDetails(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        PrintWriter out = response.getWriter();

        String id            = request.getParameter("id");
        String specialty     = request.getParameter("specialty");
        String experienceStr = request.getParameter("experienceYears");
        String feeStr        = request.getParameter("fee");

        // Validate all fields present
        if (id == null || specialty == null || specialty.trim().isEmpty()
                || experienceStr == null || feeStr == null) {
            out.write("{\"success\":false,\"message\":\"All fields are required.\"}");
            return;
        }

        int    experienceYears;
        double fee;

        try {
            experienceYears = Integer.parseInt(experienceStr.trim());
            fee             = Double.parseDouble(feeStr.trim());
        } catch (NumberFormatException e) {
            out.write("{\"success\":false,\"message\":\"Invalid experience or fee value.\"}");
            return;
        }

        if (fee < 0 || experienceYears < 0) {
            out.write("{\"success\":false,\"message\":\"Fee and experience must be positive.\"}");
            return;
        }

        try (Connection conn = com.healthcare.util.DBConnection.getConnection()) {

            String sql = "UPDATE users SET " +
                         "status = 'APPROVED', " +
                         "specialty = ?, " +
                         "experience_years = ?, " +
                         "consultation_fee = ? " +
                         "WHERE id = ? AND role = 'counsellor'";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, specialty.trim());
            ps.setInt(2, experienceYears);
            ps.setDouble(3, fee);
            ps.setInt(4, Integer.parseInt(id));

            int rows = ps.executeUpdate();

            if (rows > 0) {
                out.write("{\"success\":true,\"message\":\"Counsellor approved successfully!\"}");
            } else {
                out.write("{\"success\":false,\"message\":\"Counsellor not found.\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            out.write("{\"success\":false,\"message\":\"DB error: " + escape(e.getMessage()) + "\"}");
        }
    }

    // ── REJECT ONLY ────────────────────────────────────────────
    private void updateStatus(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        PrintWriter out = response.getWriter();
        String id     = request.getParameter("id");
        String status = request.getParameter("status");

        if (id == null || status == null) {
            out.write("{\"success\":false,\"message\":\"Missing parameters\"}");
            return;
        }

        // Only REJECTED is allowed via this action
        // Approval must go through approveWithDetails
        if (!status.equalsIgnoreCase("REJECTED")) {
            out.write("{\"success\":false,\"message\":\"Use approveWithDetails to approve.\"}");
            return;
        }

        try (Connection conn = com.healthcare.util.DBConnection.getConnection()) {

            String sql = "UPDATE users SET status = 'REJECTED' " +
                         "WHERE id = ? AND role = 'counsellor'";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, Integer.parseInt(id));
            int rows = ps.executeUpdate();

            if (rows > 0) {
                out.write("{\"success\":true,\"message\":\"Counsellor rejected.\"}");
            } else {
                out.write("{\"success\":false,\"message\":\"Counsellor not found.\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            out.write("{\"success\":false,\"message\":\"DB error: " + escape(e.getMessage()) + "\"}");
        }
    }

    // ── JSON escape helper ─────────────────────────────────────
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}