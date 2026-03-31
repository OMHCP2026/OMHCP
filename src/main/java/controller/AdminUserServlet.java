package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/AdminUserServlet")
public class AdminUserServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request, response)) return;

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        String search = request.getParameter("search");
        String role = request.getParameter("role");

        String sql =
        "SELECT id,name,email,phone,role,created_at,status " +
        "FROM users WHERE 1=1 ";

        if (role != null && !role.isEmpty())
            sql += " AND role=?";

        if (search != null && !search.isEmpty())
            sql += " AND (name LIKE ? OR email LIKE ?)";

        sql += " ORDER BY created_at DESC";

        try (Connection con = com.healthcare.util.DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            int index = 1;

            if (role != null && !role.isEmpty())
                ps.setString(index++, role);

            if (search != null && !search.isEmpty()) {
                ps.setString(index++, "%" + search + "%");
                ps.setString(index++, "%" + search + "%");
            }

            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {

                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"name\":\"").append(escape(rs.getString("name"))).append("\",");
                json.append("\"email\":\"").append(escape(rs.getString("email"))).append("\",");
                json.append("\"phone\":\"").append(escape(rs.getString("phone"))).append("\",");
                json.append("\"role\":\"").append(rs.getString("role")).append("\",");
                json.append("\"status\":\"").append(rs.getString("status")).append("\",");
                json.append("\"created_at\":\"").append(rs.getString("created_at")).append("\"");
                json.append("}");
            }

            json.append("]");

            out.print(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            out.print("[]");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request, response)) return;

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        String method = request.getParameter("_method");

        if ("PUT".equalsIgnoreCase(method)) {
            updateUser(request, out);
        }
        else if ("DELETE".equalsIgnoreCase(method)) {
            deleteUser(request, out);
        }
        else {
            addUser(request, out);
        }
    }

    // ADD USER
    private void addUser(HttpServletRequest request, PrintWriter out) {

        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String role = request.getParameter("role");
        String password = request.getParameter("password");

        String sql =
        "INSERT INTO users(name,email,phone,password,role,status,created_at) " +
        "VALUES(?,?,?,?,?, 'APPROVED', NOW())";

        try (Connection con = com.healthcare.util.DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, password);
            ps.setString(5, role);

            ps.executeUpdate();

            out.print("{\"success\":true}");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"error\":\"Add failed\"}");
        }
    }

    // UPDATE USER
    private void updateUser(HttpServletRequest request, PrintWriter out) {

        String id = request.getParameter("id");
        String name = request.getParameter("name");
        String phone = request.getParameter("phone");
        String role = request.getParameter("role");

        String sql =
        "UPDATE users SET name=?, phone=?, role=? WHERE id=?";

        try (Connection con = com.healthcare.util.DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, role);
            ps.setInt(4, Integer.parseInt(id));

            ps.executeUpdate();

            out.print("{\"success\":true}");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"error\":\"Update failed\"}");
        }
    }

    // DELETE USER
    private void deleteUser(HttpServletRequest request, PrintWriter out) {

        String id = request.getParameter("id");

        String sql = "DELETE FROM users WHERE id=?";

        try (Connection con = com.healthcare.util.DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, Integer.parseInt(id));

            ps.executeUpdate();

            out.print("{\"success\":true}");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"error\":\"Delete failed\"}");
        }
    }

    private boolean isAdmin(HttpServletRequest req, HttpServletResponse res) throws IOException {

        HttpSession session = req.getSession(false);

        if (session == null ||
            session.getAttribute("userId") == null ||
            !"admin".equalsIgnoreCase((String) session.getAttribute("userRole"))) {

            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().print("{\"error\":\"Unauthorized\"}");
            return false;
        }

        return true;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}