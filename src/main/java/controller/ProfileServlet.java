package controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import com.healthcare.util.DBConnection;

@WebServlet("/ProfileServlet")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,   // 1 MB
    maxFileSize = 1024 * 1024 * 10,    // 10 MB
    maxRequestSize = 1024 * 1024 * 15  // 15 MB
)
public class ProfileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Directory to store uploaded profile photos
    private static final String UPLOAD_DIR = "profile_photos";

    // GET – fetch profile
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        int userId = (int) session.getAttribute("userId");
        String jsonResponse = getProfileFromDB(userId, request);
        out.print(jsonResponse);
    }

    // POST – handle actions (multipart enabled)
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        int userId = (int) session.getAttribute("userId");
        String action = request.getParameter("action"); // Now works with @MultipartConfig

        if (action == null) {
            out.print("{\"success\":false,\"message\":\"Action not specified\"}");
            return;
        }

        String jsonResponse = "";
        switch (action) {
            case "updateProfile":
                jsonResponse = updateProfile(request, userId);
                break;
            case "changePassword":
                jsonResponse = changePassword(request, userId);
                break;
            case "deactivate":
                jsonResponse = deactivateAccount(userId);
                break;
            default:
                jsonResponse = "{\"success\":false,\"message\":\"Invalid action\"}";
        }
        out.print(jsonResponse);
    }

    // ─── FETCH PROFILE (with photo URL) ─────────────────────────────────
    private String getProfileFromDB(int userId, HttpServletRequest request) {
        String sql = "SELECT id, name, email, contact, age, address, blood_group, emergency_contact, " +
                     "specialization, experience, languages, bio, photo_url " +
                     "FROM users WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"success\":true,");
                json.append("\"profile\":{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"name\":\"").append(escapeJson(rs.getString("name"))).append("\",");
                json.append("\"email\":\"").append(escapeJson(rs.getString("email"))).append("\",");
                json.append("\"phone\":\"").append(escapeJson(rs.getString("contact"))).append("\",");
                json.append("\"age\":").append(rs.getInt("age")).append(",");
                json.append("\"address\":\"").append(escapeJson(rs.getString("address"))).append("\",");
                json.append("\"bloodGroup\":\"").append(escapeJson(rs.getString("blood_group"))).append("\",");
                json.append("\"emergencyContact\":\"").append(escapeJson(rs.getString("emergency_contact"))).append("\",");
                json.append("\"specialization\":\"").append(escapeJson(rs.getString("specialization"))).append("\",");
                json.append("\"experience\":\"").append(escapeJson(rs.getString("experience"))).append("\",");
                json.append("\"languages\":\"").append(escapeJson(rs.getString("languages"))).append("\",");
                json.append("\"bio\":\"").append(escapeJson(rs.getString("bio"))).append("\",");
                
                // Build full photo URL
                String photoUrl = rs.getString("photo_url");
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
                    photoUrl = baseUrl + "/" + UPLOAD_DIR + "/" + photoUrl;
                }
                json.append("\"photoUrl\":\"").append(escapeJson(photoUrl)).append("\"");
                json.append("}");
                json.append("}");
                return json.toString();
            } else {
                return "{\"success\":false,\"message\":\"User not found\"}";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + e.getMessage() + "\"}";
        }
    }

    // ─── UPDATE PROFILE (with photo upload) ─────────────────────────────
    private String updateProfile(HttpServletRequest request, int userId) {
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String specialization = request.getParameter("specialization");
        String experience = request.getParameter("experience");
        String languages = request.getParameter("languages");
        String bio = request.getParameter("bio");

        if (name == null || name.trim().isEmpty()) {
            return "{\"success\":false,\"message\":\"Name is required\"}";
        }

        ensureColumnsExist();

        // Handle photo upload
        String photoFileName = null;
        try {
            Part photoPart = request.getPart("photo");
            if (photoPart != null && photoPart.getSize() > 0) {
                // Create upload directory if not exists
                String uploadPath = getServletContext().getRealPath("") + File.separator + UPLOAD_DIR;
                File uploadDir = new File(uploadPath);
                if (!uploadDir.exists()) uploadDir.mkdir();

                // Generate unique filename
                String fileName = System.currentTimeMillis() + "_" + photoPart.getSubmittedFileName();
                photoPart.write(uploadPath + File.separator + fileName);
                photoFileName = fileName;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"File upload error: " + e.getMessage() + "\"}";
        }

        // Build dynamic update query
        StringBuilder sql = new StringBuilder("UPDATE users SET name = ?, contact = ?, email = ?, ");
        sql.append("specialization = ?, experience = ?, languages = ?, bio = ?");
        if (photoFileName != null) {
            sql.append(", photo_url = ?");
        }
        sql.append(" WHERE id = ?");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int index = 1;
            pstmt.setString(index++, name);
            pstmt.setString(index++, phone);
            pstmt.setString(index++, email);
            pstmt.setString(index++, specialization);
            pstmt.setString(index++, experience);
            pstmt.setString(index++, languages);
            pstmt.setString(index++, bio);
            if (photoFileName != null) {
                pstmt.setString(index++, photoFileName);
            }
            pstmt.setInt(index, userId);

            int updated = pstmt.executeUpdate();
            if (updated > 0) {
                return "{\"success\":true,\"message\":\"Profile updated successfully\"}";
            } else {
                return "{\"success\":false,\"message\":\"No changes made\"}";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + e.getMessage() + "\"}";
        }
    }

    // ─── CHANGE PASSWORD ───────────────────────────────────────────────
    private String changePassword(HttpServletRequest request, int userId) {
        String current = request.getParameter("currentPassword");
        String newPwd = request.getParameter("newPassword");

        if (current == null || newPwd == null || current.isEmpty() || newPwd.isEmpty()) {
            return "{\"success\":false,\"message\":\"All password fields are required\"}";
        }

        String verifySql = "SELECT password FROM users WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement verifyStmt = conn.prepareStatement(verifySql)) {

            verifyStmt.setInt(1, userId);
            ResultSet rs = verifyStmt.executeQuery();
            if (rs.next()) {
                String storedPwd = rs.getString("password");
                if (!storedPwd.equals(current)) {
                    return "{\"success\":false,\"message\":\"Current password is incorrect\"}";
                }
            } else {
                return "{\"success\":false,\"message\":\"User not found\"}";
            }

            String updateSql = "UPDATE users SET password = ? WHERE id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, newPwd);
                updateStmt.setInt(2, userId);
                updateStmt.executeUpdate();
                return "{\"success\":true,\"message\":\"Password changed successfully\"}";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + e.getMessage() + "\"}";
        }
    }

    // ─── DEACTIVATE ACCOUNT ────────────────────────────────────────────
    private String deactivateAccount(int userId) {
        String sql = "UPDATE users SET active = 0 WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            int updated = pstmt.executeUpdate();
            if (updated > 0) {
                return "{\"success\":true,\"message\":\"Account deactivated\"}";
            } else {
                return "{\"success\":false,\"message\":\"User not found or already inactive\"}";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"Database error: " + e.getMessage() + "\"}";
        }
    }

    // ─── ENSURE NEW COLUMNS EXIST (including photo_url) ─────────────────
    private void ensureColumnsExist() {
        String[] alterQueries = {
            "ALTER TABLE users ADD COLUMN specialization VARCHAR(100)",
            "ALTER TABLE users ADD COLUMN experience VARCHAR(50)",
            "ALTER TABLE users ADD COLUMN languages VARCHAR(100)",
            "ALTER TABLE users ADD COLUMN bio TEXT",
            "ALTER TABLE users ADD COLUMN active TINYINT DEFAULT 1",
            "ALTER TABLE users ADD COLUMN photo_url VARCHAR(255)"
        };

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String sql : alterQueries) {
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                        System.err.println("Error adding column: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error in ensureColumnsExist: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}