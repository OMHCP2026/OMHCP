package controller;

import java.io.*;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/CounsellorRatingsServlet")
public class CounsellorRatingsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}"); return;
        }

        String action = request.getParameter("action");
        int    userId = (int) session.getAttribute("userId");
        Object roleObj= session.getAttribute("userRole");
        String role   = roleObj != null ? roleObj.toString().trim().toLowerCase() : "";

        // Debug log
        System.out.println("CounsellorRatingsServlet: action=" + action + " role=" + role + " userId=" + userId);

        // ── getMyRatings — counsellor dashboard pe ──
        // Role check loose rakha hai — userId se hi data fetch hoga
        if ("getMyRatings".equals(action)) {
            out.print(getMyRatings(userId));

        // ── Admin: sabhi counsellors ki ratings ──
        } else if ("getAllRatings".equals(action) && "admin".equals(role)) {
            out.print(getAllRatings());

        // ── Admin: ek counsellor ke reviews ──
        } else if ("getReviews".equals(action) && "admin".equals(role)) {
            String cId = request.getParameter("counsellorId");
            if (cId == null) { out.print("{\"success\":false,\"message\":\"Missing counsellorId\"}"); return; }
            out.print(getReviews(Integer.parseInt(cId)));

        } else {
            out.print("{\"success\":false,\"message\":\"Invalid action: " + action + " role: " + role + "\"}");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Counsellor ki apni rating + reviews
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private String getMyRatings(int counsellorId) {
        try (Connection con = getConnection()) {

            // ── Summary ──
            String sumSql =
                "SELECT COUNT(*) AS total, " +
                "ROUND(AVG(rating_overall),1)       AS avg_overall, " +
                "ROUND(AVG(rating_communication),1) AS avg_comm, " +
                "ROUND(AVG(rating_empathy),1)       AS avg_emp, " +
                "ROUND(AVG(rating_helpfulness),1)   AS avg_help, " +
                "SUM(CASE WHEN recommend='true' THEN 1 ELSE 0 END) AS would_recommend, " +
                "SUM(CASE WHEN rating_overall=5 THEN 1 ELSE 0 END) AS star5, " +
                "SUM(CASE WHEN rating_overall=4 THEN 1 ELSE 0 END) AS star4, " +
                "SUM(CASE WHEN rating_overall=3 THEN 1 ELSE 0 END) AS star3, " +
                "SUM(CASE WHEN rating_overall=2 THEN 1 ELSE 0 END) AS star2, " +
                "SUM(CASE WHEN rating_overall=1 THEN 1 ELSE 0 END) AS star1 " +
                "FROM feedback WHERE counsellor_id=?";

            PreparedStatement ps = con.prepareStatement(sumSql);
            ps.setInt(1, counsellorId);
            ResultSet rs = ps.executeQuery();

            String summary = "null";
            if (rs.next()) {
                summary = "{"
                    + "\"totalReviews\":"    + rs.getInt("total")           + ","
                    + "\"avgOverall\":"      + rs.getDouble("avg_overall")  + ","
                    + "\"avgCommunication\":"+ rs.getDouble("avg_comm")     + ","
                    + "\"avgEmpathy\":"      + rs.getDouble("avg_emp")      + ","
                    + "\"avgHelpfulness\":"  + rs.getDouble("avg_help")     + ","
                    + "\"wouldRecommend\":"  + rs.getInt("would_recommend") + ","
                    + "\"star5\":"           + rs.getInt("star5")           + ","
                    + "\"star4\":"           + rs.getInt("star4")           + ","
                    + "\"star3\":"           + rs.getInt("star3")           + ","
                    + "\"star2\":"           + rs.getInt("star2")           + ","
                    + "\"star1\":"           + rs.getInt("star1")
                    + "}";
            }

            // ── Reviews list ──
            String revSql =
                "SELECT f.id, COALESCE(u.name,'Anonymous') AS patient_name, " +
                "f.rating_overall, f.tags, f.comment, f.recommend, " +
                "f.created_at AS submitted_at " +
                "FROM feedback f " +
                "LEFT JOIN users u ON f.patient_id = u.id " +
                "WHERE f.counsellor_id=? " +
                "ORDER BY f.created_at DESC LIMIT 20";

            PreparedStatement ps2 = con.prepareStatement(revSql);
            ps2.setInt(1, counsellorId);
            ResultSet rs2 = ps2.executeQuery();

            StringBuilder reviews = new StringBuilder("[");
            boolean first = true;
            while (rs2.next()) {
                if (!first) reviews.append(",");
                first = false;
                reviews.append("{")
                    .append("\"id\":").append(rs2.getInt("id")).append(",")
                    .append("\"patientName\":\"").append(esc(rs2.getString("patient_name"))).append("\",")
                    .append("\"ratingOverall\":").append(rs2.getInt("rating_overall")).append(",")
                    .append("\"tags\":\"").append(esc(rs2.getString("tags"))).append("\",")
                    .append("\"comment\":\"").append(esc(rs2.getString("comment"))).append("\",")
                    .append("\"recommend\":\"").append(esc(rs2.getString("recommend"))).append("\",")
                    .append("\"submittedAt\":\"").append(rs2.getString("submitted_at")).append("\"")
                    .append("}");
            }
            reviews.append("]");

            return "{\"success\":true,\"summary\":" + summary + ",\"reviews\":" + reviews + "}";

        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Admin: Sabhi counsellors ki ratings
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private String getAllRatings() {
        String sql =
            "SELECT u.id, u.name, COALESCE(u.specialty,'General Counselor') AS specialty, " +
            "COUNT(f.id) AS total, " +
            "COALESCE(ROUND(AVG(f.rating_overall),1),0) AS avg_overall, " +
            "SUM(CASE WHEN f.recommend='true' THEN 1 ELSE 0 END) AS would_recommend, " +
            "SUM(CASE WHEN f.rating_overall=5 THEN 1 ELSE 0 END) AS star5, " +
            "SUM(CASE WHEN f.rating_overall=4 THEN 1 ELSE 0 END) AS star4, " +
            "SUM(CASE WHEN f.rating_overall=3 THEN 1 ELSE 0 END) AS star3, " +
            "SUM(CASE WHEN f.rating_overall=2 THEN 1 ELSE 0 END) AS star2, " +
            "SUM(CASE WHEN f.rating_overall=1 THEN 1 ELSE 0 END) AS star1 " +
            "FROM users u LEFT JOIN feedback f ON u.id=f.counsellor_id " +
            "WHERE u.role='counsellor' GROUP BY u.id,u.name,u.specialty " +
            "ORDER BY avg_overall DESC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("{\"success\":true,\"ratings\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"counsellorId\":").append(rs.getInt("id")).append(",")
                    .append("\"counsellorName\":\"").append(esc(rs.getString("name"))).append("\",")
                    .append("\"specialty\":\"").append(esc(rs.getString("specialty"))).append("\",")
                    .append("\"totalReviews\":").append(rs.getInt("total")).append(",")
                    .append("\"avgOverall\":").append(rs.getDouble("avg_overall")).append(",")
                    .append("\"wouldRecommend\":").append(rs.getInt("would_recommend")).append(",")
                    .append("\"star5\":").append(rs.getInt("star5")).append(",")
                    .append("\"star4\":").append(rs.getInt("star4")).append(",")
                    .append("\"star3\":").append(rs.getInt("star3")).append(",")
                    .append("\"star2\":").append(rs.getInt("star2")).append(",")
                    .append("\"star1\":").append(rs.getInt("star1"))
                    .append("}");
            }
            return json.append("]}").toString();

        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Admin: Ek counsellor ke reviews
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private String getReviews(int counsellorId) {
        String sql =
            "SELECT f.id, u.name AS patient_name, f.rating_overall, " +
            "f.tags, f.comment, f.recommend, f.created_at AS submitted_at " +
            "FROM feedback f JOIN users u ON f.patient_id=u.id " +
            "WHERE f.counsellor_id=? ORDER BY f.created_at DESC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, counsellorId);
            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("{\"success\":true,\"reviews\":[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"patientName\":\"").append(esc(rs.getString("patient_name"))).append("\",")
                    .append("\"ratingOverall\":").append(rs.getInt("rating_overall")).append(",")
                    .append("\"tags\":\"").append(esc(rs.getString("tags"))).append("\",")
                    .append("\"comment\":\"").append(esc(rs.getString("comment"))).append("\",")
                    .append("\"recommend\":\"").append(esc(rs.getString("recommend"))).append("\",")
                    .append("\"submittedAt\":\"").append(rs.getString("submitted_at")).append("\"")
                    .append("}");
            }
            return json.append("]}").toString();

        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    private String esc(String s) {
        return s == null ? "" :
            s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");
    }
    private Connection getConnection() throws SQLException {
        return com.healthcare.util.DBConnection.getConnection();
    }
}