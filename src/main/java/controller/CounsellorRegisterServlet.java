package controller;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.healthcare.util.DBConnection;

@WebServlet("/counsellor/register")
public class CounsellorRegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String contact = request.getParameter("contact");
        String specialty = request.getParameter("specialty");
        String feeStr = request.getParameter("consultation_fee");

        double consultationFee = (feeStr != null && !feeStr.isEmpty()) ? Double.parseDouble(feeStr) : 0.0;

        String hashedPassword = md5(password);
        if (hashedPassword == null) {
            response.sendRedirect("counsellorRegister.jsp?error=1");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            String sql = "INSERT INTO users (name, email, password, role, contact, specialty, consultation_fee, status) VALUES (?, ?, ?, 'counsellor', ?, ?, ?, 'PENDING')";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, hashedPassword);
            stmt.setString(4, contact);
            stmt.setString(5, specialty);
            stmt.setDouble(6, consultationFee);
            stmt.executeUpdate();
            response.sendRedirect("counsellorLogin.jsp?msg=registered");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("counsellorRegister.jsp?error=1");
        }
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
}