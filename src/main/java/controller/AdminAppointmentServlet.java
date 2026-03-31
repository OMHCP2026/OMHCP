package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/AdminAppointmentServlet")
public class AdminAppointmentServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request, response)) return;

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        String status = request.getParameter("status");
        String date = request.getParameter("date");

        String sql =
        "SELECT a.id, " +
        "p.name AS patient, " +
        "c.name AS counsellor, " +
        "a.appointment_date, " +
        "a.appointment_time, " +
        "a.status " +
        "FROM appointments a " +
        "JOIN users p ON a.patient_id = p.id " +
        "JOIN users c ON a.counsellor_id = c.id " +
        "WHERE 1=1 ";

        if (status != null && !status.isEmpty())
            sql += " AND a.status=?";

        if (date != null && !date.isEmpty())
            sql += " AND DATE(a.appointment_date)=?";

        sql += " ORDER BY a.appointment_date DESC";

        try (Connection con = com.healthcare.util.DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            int index = 1;

            if (status != null && !status.isEmpty())
                ps.setString(index++, status);

            if (date != null && !date.isEmpty())
                ps.setString(index++, date);

            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {

                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"patient\":\"").append(escape(rs.getString("patient"))).append("\",");
                json.append("\"counsellor\":\"").append(escape(rs.getString("counsellor"))).append("\",");
                json.append("\"date\":\"").append(rs.getString("appointment_date")).append("\",");
                json.append("\"time\":\"").append(rs.getString("appointment_time")).append("\",");
                json.append("\"status\":\"").append(rs.getString("status")).append("\"");
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

        String id = request.getParameter("id");
        String status = request.getParameter("status");

        if (id == null || status == null) {
            out.print("{\"success\":false,\"error\":\"Missing parameters\"}");
            return;
        }

        String sql = "UPDATE appointments SET status=? WHERE id=?";

        try (Connection con = com.healthcare.util.DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, Integer.parseInt(id));

            int rows = ps.executeUpdate();

            if (rows > 0) {
                out.print("{\"success\":true}");
            } else {
                out.print("{\"success\":false,\"error\":\"Appointment not found\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"error\":\"Database error\"}");
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