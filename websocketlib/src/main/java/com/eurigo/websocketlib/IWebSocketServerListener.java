package com.eurigo.websocketlib;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * @author eurigo
 * Created on 2025/3/6 17:16
 * desc   : WebServerListener
 */
public interface IWebSocketServerListener {

    /**
     * 服务器已启动
     */
    void onWsOpen(WebSocket conn, ClientHandshake handshake);

    /**
     * 服务器已关闭
     */
    void onWsClose(WebSocket conn, int code, String reason, boolean remote);

    /**
     * 接收到消息
     */
    void onWsMessage(WebSocket conn, String message);

    /**
     * 连接异常
     *
     * @param conn 客户端
     * @param ex   异常
     */
    void onWsError(WebSocket conn, Exception ex);

    /**
     * 已启动
     */
    void onWsStart(WebSocketServer server);
}
