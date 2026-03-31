package controller;

import java.io.*;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.util.logging.*;

@WebServlet("/FeedbackServlet")
public class FeedbackServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(FeedbackServlet.class.getName());

    // ── GET ────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setJson(response);
        // Allow cross-origin for same-server requests
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();

        String action = request.getParameter("action");

        // ── PUBLIC endpoint — no login required ──
        if ("getAllFeedback".equals(action)) {
            out.print(getAllFeedback(request));
            return;
        }

        // ── All other actions require login ──
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            out.print(error("Please login first")); return;
        }

        int patientId = (int) session.getAttribute("userId");

        switch (action == null ? "" : action) {
            case "getCounsellors": out.print(getCounsellors());         break;
            case "getMyFeedback":  out.print(getMyFeedback(patientId)); break;
            default: out.print(error("Invalid action"));
        }
    }

    // ── POST ───────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setJson(response);
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            out.print(error("Please login first")); return;
        }

        int    patientId = (int) session.getAttribute("userId");
        String action    = request.getParameter("action");

        if ("submitFeedback".equals(action)) {
            out.print(submitFeedback(request, patientId));
        } else {
            out.print(error("Invalid action"));
        }
    }

    // ── GET: All Feedback — PUBLIC (for index page) ──────────────────
    // Returns all approved reviews with patient first name + last initial,
    // counsellor name/specialty, rating, comment, tags, recommend, date.
    // Optional query params: limit (default 50), offset (default 0)
    private String getAllFeedback(HttpServletRequest request) {
        int limit  = parseIntSafe(request.getParameter("limit"),  50);
        int offset = parseIntSafe(request.getParameter("offset"),  0);
        // Clamp to safe range
        if (limit  < 1 || limit  > 100) limit  = 50;
        if (offset < 0)                  offset = 0;

        // Patient name stored as single 'name' column.
        // We display it as: first word + " " + initial of second word + "."
        // e.g.  "Deepa Nair"  →  "Deepa N."
        // If only one word, just show that word.
        String sql =
            "SELECT f.id, " +
            "CASE " +
            "  WHEN LOCATE(' ', TRIM(p.name)) > 0 " +
            "  THEN CONCAT(SUBSTRING_INDEX(TRIM(p.name),' ',1), ' ', LEFT(SUBSTRING_INDEX(TRIM(p.name),' ',-1),1), '.') " +
            "  ELSE TRIM(p.name) " +
            "END AS patient_display, " +
            "'' AS patient_role, " +
            "u.name  AS counsellor_name, " +
            "COALESCE(u.specialty,'Counsellor') AS specialty, " +
            "f.rating_overall, f.tags, f.comment, f.recommend, " +
            "f.created_at " +
            "FROM feedback f " +
            "JOIN users u ON f.counsellor_id = u.id " +
            "JOIN users p ON f.patient_id    = p.id " +
            "WHERE f.comment IS NOT NULL AND f.comment != '' " +
            "ORDER BY f.created_at DESC " +
            "LIMIT ? OFFSET ?";

        // Also fetch aggregate stats in a second query
        String statsSql =
            "SELECT COUNT(*) AS total, " +
            "ROUND(AVG(rating_overall), 1) AS avg_rating, " +
            "SUM(CASE WHEN recommend='yes' THEN 1 ELSE 0 END) AS recommend_count, " +
            "SUM(CASE WHEN created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS this_week, " +
            "SUM(CASE WHEN rating_overall=5 THEN 1 ELSE 0 END) AS star5, " +
            "SUM(CASE WHEN rating_overall=4 THEN 1 ELSE 0 END) AS star4, " +
            "SUM(CASE WHEN rating_overall=3 THEN 1 ELSE 0 END) AS star3, " +
            "SUM(CASE WHEN rating_overall=2 THEN 1 ELSE 0 END) AS star2, " +
            "SUM(CASE WHEN rating_overall=1 THEN 1 ELSE 0 END) AS star1 " +
            "FROM feedback";

        try (Connection con = getConnection()) {

            // ── Stats ──
            String statsJson;
            try (PreparedStatement ps = con.prepareStatement(statsSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int    total    = rs.getInt("total");
                    double avg      = rs.getDouble("avg_rating");
                    int    recCount = rs.getInt("recommend_count");
                    int    thisWeek = rs.getInt("this_week");
                    int    s5 = rs.getInt("star5");
                    int    s4 = rs.getInt("star4");
                    int    s3 = rs.getInt("star3");
                    int    s2 = rs.getInt("star2");
                    int    s1 = rs.getInt("star1");
                    int    recPct = total > 0 ? (int) Math.round(recCount * 100.0 / total) : 0;

                    // Bar percentages
                    double p5 = total > 0 ? Math.round(s5 * 1000.0 / total) / 10.0 : 0;
                    double p4 = total > 0 ? Math.round(s4 * 1000.0 / total) / 10.0 : 0;
                    double p3 = total > 0 ? Math.round(s3 * 1000.0 / total) / 10.0 : 0;
                    double p2 = total > 0 ? Math.round(s2 * 1000.0 / total) / 10.0 : 0;
                    double p1 = total > 0 ? Math.round(s1 * 1000.0 / total) / 10.0 : 0;

                    statsJson = "\"stats\":{" +
                        "\"total\":"      + total   + "," +
                        "\"avgRating\":"  + avg     + "," +
                        "\"recommendPct\":" + recPct + "," +
                        "\"thisWeek\":"   + thisWeek + "," +
                        "\"pct5\":"       + p5      + "," +
                        "\"pct4\":"       + p4      + "," +
                        "\"pct3\":"       + p3      + "," +
                        "\"pct2\":"       + p2      + "," +
                        "\"pct1\":"       + p1      +
                    "}";
                } else {
                    statsJson = "\"stats\":{\"total\":0,\"avgRating\":0,\"recommendPct\":0,\"thisWeek\":0,\"pct5\":0,\"pct4\":0,\"pct3\":0,\"pct2\":0,\"pct1\":0}";
                }
            }

            // ── Reviews ──
            StringBuilder reviews = new StringBuilder();
            reviews.append("\"reviews\":[");
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                ResultSet rs = ps.executeQuery();
                boolean first = true;
                while (rs.next()) {
                    if (!first) reviews.append(",");
                    first = false;

                    String tagsRaw = rs.getString("tags");
                    String tagsJson = tagsRaw == null ? "[]" : buildTagsArray(tagsRaw);

                    reviews.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"name\":\"").append(esc(rs.getString("patient_display"))).append("\",")
                        .append("\"role\":\"").append(esc(rs.getString("patient_role"))).append("\",")
                        .append("\"counsellorName\":\"").append(esc(rs.getString("counsellor_name"))).append("\",")
                        .append("\"specialty\":\"").append(esc(rs.getString("specialty"))).append("\",")
                        .append("\"rating\":").append(rs.getInt("rating_overall")).append(",")
                        .append("\"comment\":\"").append(esc(rs.getString("comment"))).append("\",")
                        .append("\"tags\":").append(tagsJson).append(",")
                        .append("\"recommend\":\"").append(esc(rs.getString("recommend"))).append("\",")
                        .append("\"createdAt\":\"").append(rs.getString("created_at")).append("\"")
                        .append("}");
                }
            }
            reviews.append("]");

            return "{\"success\":true," + statsJson + "," + reviews + "}";

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getAllFeedback error", e);
            return error("Database error: " + e.getMessage());
        }
    }

    // Convert comma-separated tags string to JSON array
    private String buildTagsArray(String tags) {
        String[] parts = tags.split(",");
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(parts[i].trim())).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── GET: All Counsellors ─────────────────────────
    private String getCounsellors() {
        String sql =
            "SELECT id, name, specialty, consultation_fee AS fee " +
            "FROM users WHERE role='counsellor' AND is_allowed=1 " +
            "ORDER BY name ASC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("{\"success\":true,\"counsellors\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"name\":\"").append(esc(rs.getString("name"))).append("\",")
                    .append("\"specialty\":\"").append(esc(rs.getString("specialty"))).append("\",")
                    .append("\"fee\":\"").append(rs.getString("fee")).append("\"")
                    .append("}");
            }
            json.append("]}");
            return json.toString();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getCounsellors error", e);
            return error("Database error: " + e.getMessage());
        }
    }

    // ── POST: Submit Feedback ─────────────────────────
    private String submitFeedback(HttpServletRequest request, int patientId) {
        String cIdStr   = request.getParameter("counsellorId");
        String rOverall = request.getParameter("ratingOverall");
        String rComm    = request.getParameter("ratingCommunication");
        String rEmp     = request.getParameter("ratingEmpathy");
        String rHelp    = request.getParameter("ratingHelpfulness");
        String tags     = request.getParameter("tags");
        String comment  = request.getParameter("comment");
        String recommend= request.getParameter("recommend");

        if (isBlank(cIdStr) || isBlank(rOverall))
            return error("Please select a counsellor and give a rating");

        int counsellorId, overall;
        try {
            counsellorId = Integer.parseInt(cIdStr);
            overall      = Integer.parseInt(rOverall);
        } catch (NumberFormatException e) {
            return error("Invalid input values");
        }

        if (overall < 1 || overall > 5) return error("Rating must be 1 to 5");

        try (Connection con = getConnection()) {

            PreparedStatement chk = con.prepareStatement(
                "SELECT id FROM users WHERE id=? AND role='counsellor'");
            chk.setInt(1, counsellorId);
            if (!chk.executeQuery().next())
                return error("Counsellor not found");

            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO feedback " +
                "(patient_id, counsellor_id, rating_overall, " +
                "rating_communication, rating_empathy, rating_helpfulness, " +
                "tags, comment, recommend, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,NOW())");
            ps.setInt(1, patientId);
            ps.setInt(2, counsellorId);
            ps.setInt(3, overall);
            ps.setInt(4, parseIntSafe(rComm, 0));
            ps.setInt(5, parseIntSafe(rEmp,  0));
            ps.setInt(6, parseIntSafe(rHelp, 0));
            ps.setString(7, isBlank(tags)     ? null : tags);
            ps.setString(8, isBlank(comment)  ? null : comment);
            ps.setString(9, isBlank(recommend)? null : recommend);
            ps.executeUpdate();

            LOGGER.info("Feedback submitted — patient=" + patientId + " counsellor=" + counsellorId);
            return "{\"success\":true,\"message\":\"Feedback submitted successfully!\"}";

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "submitFeedback error", e);
            return error("Database error: " + e.getMessage());
        }
    }

    // ── GET: My Feedback History ──────────────────────
    private String getMyFeedback(int patientId) {
        String sql =
            "SELECT f.id, u.name AS counsellor_name, " +
            "COALESCE(u.specialty,'Counsellor') AS specialty, " +
            "f.rating_overall, f.rating_communication, " +
            "f.rating_empathy, f.rating_helpfulness, " +
            "f.tags, f.comment, f.recommend, " +
            "f.created_at AS submitted_at " +
            "FROM feedback f " +
            "JOIN users u ON f.counsellor_id = u.id " +
            "WHERE f.patient_id = ? " +
            "ORDER BY f.created_at DESC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("{\"success\":true,\"feedbacks\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"counsellorName\":\"").append(esc(rs.getString("counsellor_name"))).append("\",")
                    .append("\"specialty\":\"").append(esc(rs.getString("specialty"))).append("\",")
                    .append("\"ratingOverall\":").append(rs.getInt("rating_overall")).append(",")
                    .append("\"ratingCommunication\":").append(rs.getInt("rating_communication")).append(",")
                    .append("\"ratingEmpathy\":").append(rs.getInt("rating_empathy")).append(",")
                    .append("\"ratingHelpfulness\":").append(rs.getInt("rating_helpfulness")).append(",")
                    .append("\"tags\":\"").append(esc(rs.getString("tags"))).append("\",")
                    .append("\"comment\":\"").append(esc(rs.getString("comment"))).append("\",")
                    .append("\"recommend\":\"").append(esc(rs.getString("recommend"))).append("\",")
                    .append("\"submittedAt\":\"").append(rs.getString("submitted_at")).append("\"")
                    .append("}");
            }
            json.append("]}");
            return json.toString();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "getMyFeedback error", e);
            return error("Database error: " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────
    private void setJson(HttpServletResponse r) {
        r.setContentType("application/json");
        r.setCharacterEncoding("UTF-8");
    }
    private String error(String msg) {
        return "{\"success\":false,\"message\":\"" + esc(msg) + "\"}";
    }
    private String esc(String s) {
        return s == null ? "" :
            s.replace("\\","\\\\").replace("\"","\\\"")
             .replace("\n","\\n").replace("\r","");
    }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private Connection getConnection() throws SQLException {
        return com.healthcare.util.DBConnection.getConnection();
    }
}