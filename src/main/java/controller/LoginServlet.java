package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import com.healthcare.util.DBConnection;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String email         = request.getParameter("email");
        String password      = request.getParameter("password");
        String submittedRole = request.getParameter("role");
        String remember      = request.getParameter("remember");

        if (isEmpty(email) || isEmpty(password)) {
            out.print("{\"success\":false,\"message\":\"Email and password are required.\"}");
            return;
        }

        if (isEmpty(submittedRole)) {
            out.print("{\"success\":false,\"message\":\"Please select a role.\"}");
            return;
        }

        email = email.trim().toLowerCase();

        String sql = "SELECT id, name, email, role, password, status FROM users WHERE email=?";

        try (Connection conn = DBConnection.getConnection()) {

            if (conn == null) {
                out.print("{\"success\":false,\"message\":\"Database connection failed.\"}");
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, email);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        out.print("{\"success\":false,\"message\":\"Invalid email or password.\"}");
                        return;
                    }

                    String storedPwd = rs.getString("password");
                    String dbRole    = rs.getString("role");
                    String status    = rs.getString("status");
                    int userId       = rs.getInt("id");
                    String name      = rs.getString("name");

                    String hashedInput = md5(password);

                    if (hashedInput == null || !hashedInput.equalsIgnoreCase(storedPwd)) {
                        out.print("{\"success\":false,\"message\":\"Invalid email or password.\"}");
                        return;
                    }

                    if (dbRole != null) {
                        dbRole = dbRole.trim();
                    }

                    if (!submittedRole.equalsIgnoreCase(dbRole)) {
                        out.print("{\"success\":false,\"message\":\"Selected role does not match this account.\"}");
                        return;
                    }

                    // Counsellor approval validation – only new counsellors need approval
                    if ("counsellor".equalsIgnoreCase(dbRole)) {

                        if ("PENDING".equalsIgnoreCase(status)) {
                            out.print("{\"success\":false,\"message\":\"Your account is waiting for admin approval.\"}");
                            return;
                        }

                        if ("REJECTED".equalsIgnoreCase(status)) {
                            out.print("{\"success\":false,\"message\":\"Your account was rejected by admin.\"}");
                            return;
                        }
                        // If status is APPROVED, login proceeds normally
                    }

                    HttpSession session = request.getSession(true);
                    session.setAttribute("userId", userId);
                    session.setAttribute("userName", name);
                    session.setAttribute("userRole", dbRole);
                    session.setMaxInactiveInterval(30 * 60);

                    if ("on".equals(remember)) {
                        Cookie c = new Cookie("rememberEmail", email);
                        c.setMaxAge(7 * 24 * 60 * 60);
                        c.setPath("/");
                        c.setHttpOnly(true);
                        response.addCookie(c);
                    } else {
                        Cookie c = new Cookie("rememberEmail", "");
                        c.setMaxAge(0);
                        c.setPath("/");
                        response.addCookie(c);
                    }

                    out.print(String.format(
                            "{\"success\":true,\"userId\":%d,\"name\":\"%s\",\"role\":\"%s\"}",
                            userId, escapeJson(name), escapeJson(dbRole)
                    ));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"message\":\"Database error occurred.\"}");
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            BigInteger bi = new BigInteger(1, hash);
            String hex = bi.toString(16);
            while (hex.length() < 32) hex = "0" + hex;
            return hex;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}