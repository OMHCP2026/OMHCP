package com.healthcare.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL = "jdbc:mysql://localhost:3306/onlinehealth?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";               // Your DB username
    private static final String PASS = "ra9096296534@";     // Your DB password

    public static Connection getConnection() {
        try {
            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Return a new connection
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Connection Failed! Check DB name, user, password, or server.");
            e.printStackTrace();
        }
        return null;
    }

    // Test connection
    public static void main(String[] args) {
        if (getConnection() != null) {
            System.out.println("Connected to MySQL successfully!");
        } else {
            System.out.println("Connection failed!");
        }
    }
}
