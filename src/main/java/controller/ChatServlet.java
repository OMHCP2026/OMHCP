package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import com.healthcare.util.DBConnection;

/**
 * ChatServlet – handles all messaging between patients and counsellors.
 *
 * GET actions : getConversations, getMessages, getUnreadCount, getAvailableUsers
 * POST actions: sendMessage, markAsRead
 *
 * Role rules enforced:
 *   - Patient    : can send to counsellors only
 *   - Counsellor : can reply to patients only
 *   - Admin      : can message anyone
 */
@WebServlet("/ChatServlet")
public class ChatServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private void setup(HttpServletResponse res) {
        res.setContentType("application/json;charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setHeader("Pragma", "no-cache");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        setup(res);
        PrintWriter out     = res.getWriter();
        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        int    userId = (int)    session.getAttribute("userId");
        String role   = (String) session.getAttribute("userRole");
        String action = req.getParameter("action");

        if (action == null) {
            out.print("{\"success\":false,\"message\":\"Missing action parameter\"}");
            return;
        }

        switch (action) {
            case "getConversations":
                out.print(getConversations(userId));
                break;
            case "getMessages":
                int otherId = intParam(req, "otherUserId", 0);
                if (otherId == 0) {
                    out.print("{\"success\":false,\"message\":\"otherUserId required\"}");
                    return;
                }
                out.print(getMessages(userId, otherId));
                break;
            case "getUnreadCount":
                out.print(getUnreadCount(userId));
                break;
            case "getAvailableUsers":
                out.print(getAvailableUsers(userId, role));
                break;
            default:
                out.print("{\"success\":false,\"message\":\"Unknown action: " + escJson(action) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        setup(res);
        req.setCharacterEncoding("UTF-8");
        PrintWriter out     = res.getWriter();
        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        int    userId = (int)    session.getAttribute("userId");
        String role   = (String) session.getAttribute("userRole");
        String action = req.getParameter("action");

        if (action == null) {
            out.print("{\"success\":false,\"message\":\"Missing action parameter\"}");
            return;
        }

        switch (action) {
            case "sendMessage":
                int    receiverId = intParam(req, "receiverId", 0);
                String message    = req.getParameter("message");

                if (receiverId == 0) {
                    out.print("{\"success\":false,\"message\":\"receiverId required\"}");
                    return;
                }
                if (message == null || message.trim().isEmpty()) {
                    out.print("{\"success\":false,\"message\":\"message required\"}");
                    return;
                }

                // Enforce role permissions
                String permErr = checkPermission(userId, role, receiverId);
                if (permErr != null) {
                    out.print("{\"success\":false,\"message\":\"" + escJson(permErr) + "\"}");
                    return;
                }

                out.print(sendMessage(userId, receiverId, message.trim()));
                break;

            case "markAsRead":
                int otherId2 = intParam(req, "otherUserId", 0);
                if (otherId2 == 0) {
                    out.print("{\"success\":false,\"message\":\"otherUserId required\"}");
                    return;
                }
                out.print(markAsRead(userId, otherId2));
                break;

            default:
                out.print("{\"success\":false,\"message\":\"Unknown action: " + escJson(action) + "\"}");
        }
    }

    // ---------- getConversations ----------
    private String getConversations(int userId) {
        String sql =
            "SELECT " +
            "  partner_id," +
            "  (SELECT name FROM users WHERE id = partner_id) AS partner_name," +
            "  (SELECT role FROM users WHERE id = partner_id) AS partner_role," +
            "  (" +
            "    SELECT message FROM chat_messages m2" +
            "    WHERE (m2.sender_id = ? AND m2.receiver_id = partner_id)" +
            "       OR (m2.sender_id = partner_id AND m2.receiver_id = ?)" +
            "    ORDER BY m2.created_at DESC LIMIT 1" +
            "  ) AS last_msg," +
            "  (" +
            "    SELECT UNIX_TIMESTAMP(created_at) * 1000 FROM chat_messages m3" +
            "    WHERE (m3.sender_id = ? AND m3.receiver_id = partner_id)" +
            "       OR (m3.sender_id = partner_id AND m3.receiver_id = ?)" +
            "    ORDER BY m3.created_at DESC LIMIT 1" +
            "  ) AS last_ts," +
            "  (" +
            "    SELECT COUNT(*) FROM chat_messages cu" +
            "    WHERE cu.sender_id = partner_id AND cu.receiver_id = ? AND cu.is_read = FALSE" +
            "  ) AS unread_count " +
            "FROM (" +
            "  SELECT DISTINCT" +
            "    CASE WHEN sender_id = ? THEN receiver_id ELSE sender_id END AS partner_id" +
            "  FROM chat_messages" +
            "  WHERE sender_id = ? OR receiver_id = ?" +
            ") partners " +
            "ORDER BY last_ts DESC";

        List<Map<String,Object>> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ps.setInt(4, userId);
            ps.setInt(5, userId);
            ps.setInt(6, userId);
            ps.setInt(7, userId);
            ps.setInt(8, userId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("userId",          rs.getInt("partner_id"));
                row.put("userName",        safe(rs.getString("partner_name")));
                row.put("userRole",        safe(rs.getString("partner_role")));
                row.put("lastMessage",     safe(rs.getString("last_msg")));
                row.put("lastMessageTime", rs.getLong("last_ts"));
                row.put("unreadCount",     rs.getInt("unread_count"));
                list.add(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escJson(e.getMessage()) + "\"}";
        }

        return "{\"success\":true,\"conversations\":" + toJsonArray(list) + "}";
    }

    // ---------- getMessages ----------
    private String getMessages(int userId, int otherId) {
        String sql =
            "SELECT id, sender_id, receiver_id, message, is_read," +
            "  UNIX_TIMESTAMP(created_at) * 1000 AS created_ts " +
            "FROM chat_messages " +
            "WHERE (sender_id = ? AND receiver_id = ?)" +
            "   OR (sender_id = ? AND receiver_id = ?) " +
            "ORDER BY created_at ASC";

        List<Map<String,Object>> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, otherId);
            ps.setInt(3, otherId);
            ps.setInt(4, userId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("id",         rs.getInt("id"));
                row.put("senderId",   rs.getInt("sender_id"));
                row.put("receiverId", rs.getInt("receiver_id"));
                row.put("message",    safe(rs.getString("message")));
                row.put("isRead",     rs.getBoolean("is_read"));
                row.put("createdAt",  rs.getLong("created_ts"));
                // isMine flag is computed in frontend using currentUserId
                list.add(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escJson(e.getMessage()) + "\"}";
        }

        return "{\"success\":true,\"messages\":" + toJsonArray(list) + "}";
    }

    // ---------- sendMessage ----------
    private String sendMessage(int senderId, int receiverId, String message) {
        String sql =
            "INSERT INTO chat_messages (sender_id, receiver_id, message, is_read, created_at)" +
            " VALUES (?, ?, ?, FALSE, NOW())";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setString(3, message);

            int rows = ps.executeUpdate();
            if (rows == 0) return "{\"success\":false,\"message\":\"Insert failed\"}";

            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : -1;

            return "{\"success\":true,\"messageId\":" + newId + "}";

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escJson(e.getMessage()) + "\"}";
        }
    }

    // ---------- markAsRead ----------
    private String markAsRead(int readerId, int senderId) {
        String sql =
            "UPDATE chat_messages SET is_read = TRUE" +
            " WHERE receiver_id = ? AND sender_id = ? AND is_read = FALSE";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, readerId);
            ps.setInt(2, senderId);
            int updated = ps.executeUpdate();
            return "{\"success\":true,\"updated\":" + updated + "}";

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escJson(e.getMessage()) + "\"}";
        }
    }

    // ---------- getUnreadCount ----------
    private String getUnreadCount(int userId) {
        String sql = "SELECT COUNT(*) AS n FROM chat_messages WHERE receiver_id = ? AND is_read = FALSE";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            int n = rs.next() ? rs.getInt("n") : 0;
            return "{\"success\":true,\"unreadCount\":" + n + "}";

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":true,\"unreadCount\":0}";
        }
    }

    // ---------- getAvailableUsers ----------
    private String getAvailableUsers(int userId, String role) {
        String targetRole = null;
        if ("patient".equalsIgnoreCase(role))         targetRole = "counsellor";
        else if ("counsellor".equalsIgnoreCase(role)) targetRole = "patient";
        // admin: targetRole stays null → see all

        String sql = (targetRole != null)
            ? "SELECT id, name, role FROM users WHERE role = ? AND id != ? ORDER BY name"
            : "SELECT id, name, role FROM users WHERE id != ? ORDER BY name";

        List<Map<String,Object>> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (targetRole != null) {
                ps.setString(1, targetRole);
                ps.setInt(2, userId);
            } else {
                ps.setInt(1, userId);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String,Object> u = new LinkedHashMap<>();
                u.put("id",   rs.getInt("id"));
                u.put("name", safe(rs.getString("name")));
                u.put("role", safe(rs.getString("role")));
                list.add(u);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"success\":false,\"message\":\"" + escJson(e.getMessage()) + "\"}";
        }

        return "{\"success\":true,\"users\":" + toJsonArray(list) + "}";
    }

    // ---------- permission check ----------
    private String checkPermission(int senderId, String senderRole, int receiverId) {
        if ("admin".equalsIgnoreCase(senderRole)) return null; // admin: no restriction

        String requiredReceiverRole = "patient".equalsIgnoreCase(senderRole) ? "counsellor" : "patient";

        String sql = "SELECT role FROM users WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, receiverId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return "Recipient not found";

            String receiverRole = rs.getString("role");
            if (!requiredReceiverRole.equalsIgnoreCase(receiverRole)) {
                return "You can only message a " + requiredReceiverRole;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Fail open – let the message go through if DB error on permission check
        }

        return null; // OK
    }

    // ---------- JSON utilities ----------
    private String toJsonArray(List<Map<String,Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJsonObj(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonObj(Map<String,Object> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String,Object> e : map.entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if      (v == null)          sb.append("null");
            else if (v instanceof String) sb.append("\"").append(escJson((String) v)).append("\"");
            else if (v instanceof Boolean)sb.append(v.toString());
            else                          sb.append(v.toString());
        }
        sb.append("}");
        return sb.toString();
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"",  "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }

    private String safe(String s) { return s != null ? s : ""; }

    private int intParam(HttpServletRequest req, String name, int def) {
        try {
            String v = req.getParameter(name);
            return (v != null && !v.isEmpty()) ? Integer.parseInt(v.trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}