package controller;

import java.io.*;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/AdminDashboardServlet")
public class AdminDashboardServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request, response)) return;

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection con = getConnection()) {

            int totalPatients      = count(con, "SELECT COUNT(*) FROM users WHERE role='patient'");
            int totalCounsellors   = count(con, "SELECT COUNT(*) FROM users WHERE role='counsellor'");
            int totalAppointments  = count(con, "SELECT COUNT(*) FROM appointments");
            int pendingAppts       = count(con, "SELECT COUNT(*) FROM appointments WHERE status='pending'");
            int confirmedAppts     = count(con, "SELECT COUNT(*) FROM appointments WHERE status='confirmed'");
            int completedAppts     = count(con, "SELECT COUNT(*) FROM appointments WHERE status='completed'");
            int cancelledAppts     = count(con, "SELECT COUNT(*) FROM appointments WHERE status='cancelled'");
            int todayAppts         = count(con, "SELECT COUNT(*) FROM appointments WHERE DATE(appointment_date) = CURDATE()");
            int pendingCounsellors = count(con, "SELECT COUNT(*) FROM users WHERE role='counsellor' AND status='PENDING'");

            // ✅ success:true added  |  ✅ field names match admin-dashboard.js
            out.print("{" +
                "\"success\":true,"                                        +
                "\"totalPatients\":"           + totalPatients      + "," +
                "\"totalCounsellors\":"        + totalCounsellors   + "," +
                "\"totalAppointments\":"       + totalAppointments  + "," +
                "\"pendingAppointments\":"     + pendingAppts       + "," +
                "\"confirmedAppointments\":"   + confirmedAppts     + "," +
                "\"completedAppointments\":"   + completedAppts     + "," +
                "\"cancelledAppointments\":"   + cancelledAppts     + "," +
                "\"todayAppointments\":"       + todayAppts         + "," +
                "\"pendingCounsellors\":"      + pendingCounsellors +
            "}");

        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int count(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private boolean isAdmin(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null
                || s.getAttribute("userId")   == null
                || !"admin".equalsIgnoreCase((String) s.getAttribute("userRole"))) {
            res.sendRedirect("login.html");
            return false;
        }
        return true;
    }

    private Connection getConnection() throws SQLException {
        return com.healthcare.util.DBConnection.getConnection();
    }
}