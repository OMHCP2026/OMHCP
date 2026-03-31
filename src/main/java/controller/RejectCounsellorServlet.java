package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/RejectCounsellorServlet")
public class RejectCounsellorServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/plain");

        PrintWriter out = response.getWriter();

        String id = request.getParameter("id");

        if (id == null) {
            out.print("error");
            return;
        }

        try (Connection con = com.healthcare.util.DBConnection.getConnection()) {

            String sql = "UPDATE users SET status='REJECTED' WHERE id=?";

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, Integer.parseInt(id));

            int rows = ps.executeUpdate();

            if (rows > 0)
                out.print("success");
            else
                out.print("error");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("error");
        }
    }
}