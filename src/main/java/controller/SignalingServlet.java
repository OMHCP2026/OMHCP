package controller;

import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Signaling Server for WebRTC Audio/Video Calls
 * Tomcat 9 compatible — no extra dependencies needed
 * URL: ws://localhost:8080/OMHCP/signal
 */
@ServerEndpoint("/signal")
public class SignalingServlet {

    // userId (String) → WebSocket Session  (shared across all instances)
    private static final Map<String, Session> userSessions = new ConcurrentHashMap<>();

    // ── On Connect ────────────────────────────────────────────────────────────
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        System.out.println("🔌 WS Connected: " + session.getId());
    }

    // ── On Message ────────────────────────────────────────────────────────────
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            String type   = extractJson(message, "type");
            String to     = extractJson(message, "to");
            String from   = extractJson(message, "from");
            String userId = extractJson(message, "userId");

            System.out.println("📨 Signal: " + type + " | from=" + from + " | to=" + to);

            switch (type) {

                case "register":
                    userSessions.put(userId, session);
                    session.getUserProperties().put("userId", userId);
                    System.out.println("✅ Registered user: " + userId);
                    sendTo(session, "{\"type\":\"registered\",\"userId\":\"" + userId + "\"}");
                    break;

                case "call-user":
                    Session callTarget = userSessions.get(to);
                    if (callTarget != null && callTarget.isOpen()) {
                        // Change type to "incoming-call" for the receiver
                        String incomingMsg = message.replace("\"call-user\"", "\"incoming-call\"");
                        sendTo(callTarget, incomingMsg);
                        System.out.println("📞 Call forwarded to user: " + to);
                    } else {
                        sendTo(session, "{\"type\":\"call-rejected\",\"reason\":\"User is offline\"}");
                        System.out.println("❌ Target user offline: " + to);
                    }
                    break;

                case "accept-call":
                    Session acceptTarget = userSessions.get(to);
                    if (acceptTarget != null && acceptTarget.isOpen()) {
                        String acceptMsg = message.replace("\"accept-call\"", "\"call-accepted\"");
                        sendTo(acceptTarget, acceptMsg);
                    }
                    break;

                case "reject-call":
                    Session rejectTarget = userSessions.get(to);
                    if (rejectTarget != null && rejectTarget.isOpen()) {
                        sendTo(rejectTarget, "{\"type\":\"call-rejected\"}");
                    }
                    break;

                case "end-call":
                    Session endTarget = userSessions.get(to);
                    if (endTarget != null && endTarget.isOpen()) {
                        sendTo(endTarget, "{\"type\":\"call-ended\"}");
                    }
                    break;

                case "ice-candidate":
                    Session iceTarget = userSessions.get(to);
                    if (iceTarget != null && iceTarget.isOpen()) {
                        sendTo(iceTarget, message);
                    }
                    break;

                default:
                    System.out.println("⚠️ Unknown signal type: " + type);
                    break;
            }

        } catch (Exception e) {
            System.err.println("WS onMessage error: " + e.getMessage());
        }
    }

    // ── On Close ──────────────────────────────────────────────────────────────
    @OnClose
    public void onClose(Session session) {
        String userId = (String) session.getUserProperties().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
            System.out.println("🔌 User disconnected: " + userId);
        }
    }

    // ── On Error ──────────────────────────────────────────────────────────────
    @OnError
    public void onError(Session session, Throwable t) {
        System.err.println("WS error: " + t.getMessage());
    }

    // ── Send text to a session safely ─────────────────────────────────────────
    private void sendTo(Session session, String message) {
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            System.err.println("sendTo error: " + e.getMessage());
        }
    }

    // ── Minimal JSON string/object extractor (no Gson needed) ────────────────
    private String extractJson(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        int start = idx + search.length();
        if (start >= json.length()) return "";

        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            while (end > 0 && json.charAt(end - 1) == '\\') {
                end = json.indexOf('"', end + 1);
            }
            return end > start ? json.substring(start + 1, end) : "";
        } else if (first == '{' || first == '[') {
            char open = first, close = (first == '{') ? '}' : ']';
            int depth = 0, i = start;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == open)  depth++;
                if (c == close) { depth--; if (depth == 0) return json.substring(start, i + 1); }
                i++;
            }
        }
        return "";
    }
}