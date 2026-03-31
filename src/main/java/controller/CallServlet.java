package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

/**
 * CallServlet – WebRTC signaling server for audio/video calls.
 *
 * GET  actions : getSignal, getCallStatus, checkIncomingCall
 * POST actions : initiateCall, acceptCall, rejectCall, endCall, sendSignal
 */
@WebServlet("/CallServlet")
public class CallServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // In-memory signaling store: callId -> list of signal messages
    private static final ConcurrentHashMap<String, List<Map<String,Object>>> signalStore =
        new ConcurrentHashMap<>();

    // Active calls: callId -> call info map
    private static final ConcurrentHashMap<String, Map<String,Object>> activeCalls =
        new ConcurrentHashMap<>();

    // User -> active callId mapping
    private static final ConcurrentHashMap<Integer, String> userCallMap =
        new ConcurrentHashMap<>();

    private void setup(HttpServletResponse res) {
        res.setContentType("application/json;charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setHeader("Access-Control-Allow-Origin", "*");
    }

    // ════════════════════════════════════════════════════════════════
    // GET
    // ════════════════════════════════════════════════════════════════
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        setup(res);
        PrintWriter out = res.getWriter();
        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        int userId = (int) session.getAttribute("userId");
        String action = req.getParameter("action");

        if (action == null) {
            out.print("{\"success\":false,\"message\":\"Missing action\"}");
            return;
        }

        switch (action) {
            case "getSignal":
                String callId  = req.getParameter("callId");
                String sinceStr = req.getParameter("since");
                long since = sinceStr != null ? Long.parseLong(sinceStr) : 0;
                out.print(getSignals(callId, userId, since));
                break;

            case "getCallStatus":
                out.print(getCallStatus(userId));
                break;

            case "checkIncomingCall":
                out.print(checkIncomingCall(userId));
                break;

            default:
                out.print("{\"success\":false,\"message\":\"Unknown action\"}");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // POST
    // ════════════════════════════════════════════════════════════════
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        setup(res);
        req.setCharacterEncoding("UTF-8");
        PrintWriter out = res.getWriter();
        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            out.print("{\"success\":false,\"message\":\"Not logged in\"}");
            return;
        }

        int userId = (int) session.getAttribute("userId");
        String userName = (String) session.getAttribute("userName");
        String action = req.getParameter("action");

        switch (action != null ? action : "") {

            case "initiateCall": {
                int targetUserId = intParam(req, "targetUserId", 0);
                String callType  = req.getParameter("callType"); // "audio" or "video"
                if (targetUserId == 0 || callType == null) {
                    out.print("{\"success\":false,\"message\":\"targetUserId and callType required\"}");
                    return;
                }
                out.print(initiateCall(userId, userName, targetUserId, callType));
                break;
            }

            case "acceptCall": {
                String cId = req.getParameter("callId");
                out.print(acceptCall(userId, cId));
                break;
            }

            case "rejectCall": {
                String cId = req.getParameter("callId");
                out.print(rejectCall(userId, cId));
                break;
            }

            case "endCall": {
                String cId = req.getParameter("callId");
                out.print(endCall(userId, cId));
                break;
            }

            case "sendSignal": {
                String cId  = req.getParameter("callId");
                String type = req.getParameter("type"); // "offer", "answer", "ice"
                String data = req.getParameter("data");
                out.print(sendSignal(userId, cId, type, data));
                break;
            }

            // ✅ Force-clear all calls for this user (called on page unload / cleanup)
            case "clearMyCall": {
                forceCleanupUser(userId);
                out.print("{\"success\":true}");
                break;
            }

            default:
                out.print("{\"success\":false,\"message\":\"Unknown action: " + escJson(action) + "\"}");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // initiateCall  ✅ force-clears stuck previous call
    // ════════════════════════════════════════════════════════════════
    private String initiateCall(int callerId, String callerName, int targetId, String callType) {

        // ✅ If caller is already mapped to a call, force-end it first
        if (userCallMap.containsKey(callerId)) {
            String oldCallId = userCallMap.get(callerId);
            forceEndCall(oldCallId);
            userCallMap.remove(callerId);
        }

        // ✅ If target is already in a call, clear that too
        if (userCallMap.containsKey(targetId)) {
            String oldCallId = userCallMap.get(targetId);
            forceEndCall(oldCallId);
            userCallMap.remove(targetId);
        }

        String callId = "call_" + callerId + "_" + targetId + "_" + System.currentTimeMillis();

        Map<String,Object> callInfo = new LinkedHashMap<>();
        callInfo.put("callId",     callId);
        callInfo.put("callerId",   callerId);
        callInfo.put("callerName", callerName != null ? callerName : "Unknown");
        callInfo.put("targetId",   targetId);
        callInfo.put("callType",   callType);
        callInfo.put("status",     "ringing"); // ringing, active, ended, rejected
        callInfo.put("startTime",  System.currentTimeMillis());

        activeCalls.put(callId, callInfo);
        userCallMap.put(callerId, callId);
        signalStore.put(callId, new ArrayList<>());

        return "{\"success\":true,\"callId\":\"" + escJson(callId) + "\"}";
    }

    // ════════════════════════════════════════════════════════════════
    // acceptCall
    // ════════════════════════════════════════════════════════════════
    private String acceptCall(int userId, String callId) {
        Map<String,Object> call = activeCalls.get(callId);
        if (call == null) return "{\"success\":false,\"message\":\"Call not found\"}";

        call.put("status",     "active");
        call.put("acceptTime", System.currentTimeMillis());
        userCallMap.put(userId, callId);

        return "{\"success\":true,\"callId\":\"" + escJson(callId) + "\"}";
    }

    // ════════════════════════════════════════════════════════════════
    // rejectCall
    // ════════════════════════════════════════════════════════════════
    private String rejectCall(int userId, String callId) {
        Map<String,Object> call = activeCalls.get(callId);
        if (call == null) return "{\"success\":false,\"message\":\"Call not found\"}";

        call.put("status", "rejected");
        addSignal(callId, userId, "rejected", "");
        cleanupCall(callId);

        return "{\"success\":true}";
    }

    // ════════════════════════════════════════════════════════════════
    // endCall
    // ════════════════════════════════════════════════════════════════
    private String endCall(int userId, String callId) {
        // ✅ Handle cleanup-only calls (callId = "cleanup" or null)
        if (callId == null || callId.equals("cleanup")) {
            forceCleanupUser(userId);
            return "{\"success\":true}";
        }

        Map<String,Object> call = activeCalls.get(callId);
        if (call != null) {
            call.put("status", "ended");
            addSignal(callId, userId, "ended", "");
        }
        cleanupCall(callId);
        forceCleanupUser(userId);

        return "{\"success\":true}";
    }

    // ════════════════════════════════════════════════════════════════
    // sendSignal
    // ════════════════════════════════════════════════════════════════
    private String sendSignal(int fromUserId, String callId, String type, String data) {
        if (callId == null || type == null || data == null) {
            return "{\"success\":false,\"message\":\"callId, type, and data required\"}";
        }
        addSignal(callId, fromUserId, type, data);
        return "{\"success\":true}";
    }

    // ════════════════════════════════════════════════════════════════
    // getSignals
    // ════════════════════════════════════════════════════════════════
    private String getSignals(String callId, int userId, long since) {
        if (callId == null) return "{\"success\":false,\"message\":\"callId required\"}";

        List<Map<String,Object>> signals = signalStore.get(callId);
        if (signals == null) return "{\"success\":true,\"signals\":[],\"callStatus\":\"ended\"}";

        Map<String,Object> call = activeCalls.get(callId);
        String status = call != null ? (String) call.get("status") : "ended";

        List<Map<String,Object>> filtered = new ArrayList<>();
        synchronized (signals) {
            for (Map<String,Object> sig : signals) {
                long ts   = (long) sig.get("timestamp");
                int  from = (int)  sig.get("fromUserId");
                if (ts > since && from != userId) {
                    filtered.add(sig);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"callStatus\":\"").append(escJson(status)).append("\",\"signals\":[");
        for (int i = 0; i < filtered.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String,Object> s = filtered.get(i);
            sb.append("{\"type\":\"").append(escJson((String) s.get("type"))).append("\"")
              .append(",\"data\":").append(s.get("data"))
              .append(",\"from\":").append(s.get("fromUserId"))
              .append(",\"timestamp\":").append(s.get("timestamp"))
              .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // getCallStatus
    // ════════════════════════════════════════════════════════════════
    private String getCallStatus(int userId) {
        String callId = userCallMap.get(userId);
        if (callId == null) return "{\"success\":true,\"inCall\":false}";

        Map<String,Object> call = activeCalls.get(callId);
        if (call == null) {
            userCallMap.remove(userId);
            return "{\"success\":true,\"inCall\":false}";
        }

        return "{\"success\":true,\"inCall\":true"
             + ",\"callId\":\""   + escJson(callId) + "\""
             + ",\"status\":\""   + escJson((String) call.get("status"))   + "\""
             + ",\"callType\":\"" + escJson((String) call.get("callType")) + "\""
             + "}";
    }

    // ════════════════════════════════════════════════════════════════
    // checkIncomingCall
    // ════════════════════════════════════════════════════════════════
    private String checkIncomingCall(int userId) {
        for (Map.Entry<String, Map<String,Object>> entry : activeCalls.entrySet()) {
            Map<String,Object> call   = entry.getValue();
            int    targetId  = (int)    call.get("targetId");
            String status    = (String) call.get("status");

            if (targetId == userId && "ringing".equals(status)) {
                long startTime = (long) call.get("startTime");
                // Expire rings older than 60 seconds
                if (System.currentTimeMillis() - startTime > 60000) {
                    call.put("status", "ended");
                    cleanupCall(entry.getKey());
                    continue;
                }
                return "{\"success\":true,\"hasIncoming\":true"
                     + ",\"callId\":\""     + escJson(entry.getKey())               + "\""
                     + ",\"callerName\":\"" + escJson((String) call.get("callerName")) + "\""
                     + ",\"callType\":\""   + escJson((String) call.get("callType"))   + "\""
                     + ",\"callerId\":"     + call.get("callerId")
                     + "}";
            }
        }
        return "{\"success\":true,\"hasIncoming\":false}";
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private void addSignal(String callId, int fromUserId, String type, String data) {
        List<Map<String,Object>> signals = signalStore.get(callId);
        if (signals == null) return;

        Map<String,Object> signal = new LinkedHashMap<>();
        signal.put("fromUserId", fromUserId);
        signal.put("type",       type);
        signal.put("data",       data != null && !data.isEmpty()
                                    ? "\"" + escJson(data) + "\""
                                    : "null");
        signal.put("timestamp",  System.currentTimeMillis());

        synchronized (signals) {
            signals.add(signal);
            // Keep only last 100 signals per call to avoid memory bloat
            if (signals.size() > 100) signals.remove(0);
        }
    }

    // ✅ Mark call ended and remove user mappings
    private void cleanupCall(String callId) {
        Map<String,Object> call = activeCalls.get(callId);
        if (call != null) {
            Object cId = call.get("callerId");
            Object tId = call.get("targetId");
            if (cId instanceof Integer) userCallMap.remove((Integer) cId);
            if (tId instanceof Integer) userCallMap.remove((Integer) tId);
            call.put("status", "ended");
        }
        // Remove from active + signal store after short delay would need a scheduler;
        // for now just mark ended — clients will stop polling on "ended" status
        activeCalls.remove(callId);
        signalStore.remove(callId);
    }

    // ✅ Force-end a call without needing userId
    private void forceEndCall(String callId) {
        Map<String,Object> call = activeCalls.get(callId);
        if (call != null) {
            call.put("status", "ended");
        }
        activeCalls.remove(callId);
        signalStore.remove(callId);
    }

    // ✅ Remove ALL call mappings for a specific user
    private void forceCleanupUser(int userId) {
        String callId = userCallMap.remove(userId);
        if (callId != null) {
            forceEndCall(callId);
        }
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"",  "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }

    private int intParam(HttpServletRequest req, String name, int def) {
        try {
            String v = req.getParameter(name);
            return (v != null && !v.isEmpty()) ? Integer.parseInt(v.trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}