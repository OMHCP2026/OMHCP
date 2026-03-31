package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.healthcare.util.DBConnection;

@WebServlet("/RegisterServlet")
public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String contact = request.getParameter("contact");
        String ageStr = request.getParameter("age");
        String role = request.getParameter("role");
        
        // ✅ Validate age
        int age = 0;
        try {
            age = Integer.parseInt(ageStr);
            if (age < 18) {
                showAlert(out, "Age must be 18+", "register.html");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(out, "Invalid age format", "register.html");
            return;
        }
        
        // ✅ Validate contact number
        if (contact == null || contact.trim().isEmpty()) {
            showAlert(out, "Contact number is required!", "register.html");
            return;
        }
        
        // Remove spaces/dashes if user typed them
        contact = contact.trim().replaceAll("[\\s\\-]", "");
        
        // Only digits allowed
        if (!contact.matches("\\d+")) {
            showAlert(out, "Contact number must contain digits only!", "register.html");
            return;
        }
        
        // Must be exactly 10 digits
        if (contact.length() != 10) {
            showAlert(out, "Contact number must be exactly 10 digits!", "register.html");
            return;
        }
        
        // Must start with 6, 7, 8, or 9 (valid Indian mobile numbers)
        if (!contact.matches("[6-9]\\d{9}")) {
            showAlert(out, "Enter a valid Indian mobile number (starting with 6, 7, 8 or 9)!", "register.html");
            return;
        }
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            conn = DBConnection.getConnection();
            
            if (conn == null) {
                showAlert(out, "Database connection failed!", "register.html");
                return;
            }
            
            // Hash password using MD5
            String hashedPassword = md5(password);
            
            String sql = "INSERT INTO users (name, email, password, contact, age, role) VALUES (?, ?, ?, ?, ?, ?)";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, hashedPassword);
            pstmt.setString(4, contact);
            pstmt.setInt(5, age);
            pstmt.setString(6, role);
            
            int rowsInserted = pstmt.executeUpdate();
            
            if (rowsInserted > 0) {
                showAlert(out, "Registration successful! Please login.", "login.html");
            } else {
                showAlert(out, "Registration failed. Please try again.", "register.html");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            if (e.getErrorCode() == 1062) {
                showAlert(out, "Email already exists! Please use a different email.", "register.html");
            } else {
                showAlert(out, "Database error: " + e.getMessage(), "register.html");
            }
        } catch (NoSuchAlgorithmException e) {
            showAlert(out, "Password encryption error", "register.html");
            e.printStackTrace();
        } finally {
            closeResources(conn, pstmt);
        }
    }
    
    private String md5(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] messageDigest = md.digest(input.getBytes());
        BigInteger no = new BigInteger(1, messageDigest);
        String hashtext = no.toString(16);
        while (hashtext.length() < 32) {
            hashtext = "0" + hashtext;
        }
        return hashtext;
    }
    
    private void showAlert(PrintWriter out, String message, String redirectUrl) {
        out.println("<script>");
        out.println("alert('" + message.replace("'", "\\'") + "');");
        out.println("window.location='" + redirectUrl + "';");
        out.println("</script>");
    }
    
    private void closeResources(Connection conn, PreparedStatement pstmt) {
        try {
            if (pstmt != null) pstmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}