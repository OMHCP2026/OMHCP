package controller;

import java.io.*;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;
import com.healthcare.util.DBConnection;

@WebServlet("/UserSearchServlet")
public class UserSearchServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        String role = ((String) session.getAttribute("userRole")).trim();
        if (!"counsellor".equalsIgnoreCase(role)) {
            out.print("{\"success\":false,\"message\":\"Access denied\"}");
            return;
        }

        String q          = request.getParameter("q");
        String searchRole = request.getParameter("role");

        if (q == null || q.trim().length() < 2) {
            out.print("{\"success\":true,\"users\":[]}");
            return;
        }

        String sql = "SELECT id, name, email FROM users WHERE role = ? AND name LIKE ? LIMIT 10";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, searchRole != null ? searchRole : "patient");
            ps.setString(2, "%" + q.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    if (count++ > 0) json.append(",");
                    json.append("{\"id\":").append(rs.getInt("id"))
                        .append(",\"name\":\"").append(escapeJson(rs.getString("name"))).append("\"")
                        .append(",\"email\":\"").append(escapeJson(rs.getString("email"))).append("\"}");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"message\":\"Database error\"}");
            return;
        }
        json.append("]");
        out.print("{\"success\":true,\"users\":" + json + "}");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}