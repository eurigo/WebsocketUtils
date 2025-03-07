package com.eurigo.websocketlib;

import static com.eurigo.websocketlib.WsManager.DEFAULT_WEBSOCKET;

import com.eurigo.websocketlib.util.ThreadUtils;
import com.eurigo.websocketlib.util.WsLogUtil;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eurigo
 * Created on 2022/3/29 17:14
 * Update on 2025/3/6
 * desc   : WebSocket客户端
 */
public class WsClient extends WebSocketClient {

    public WsClient(URI serverUri, Draft protocolDraft, Map<String, String> httpHeaders, int connectTimeout
            , Builder builder) {
        super(serverUri, protocolDraft, httpHeaders, connectTimeout);
        if (builder.listener == null) {
            throw new IllegalArgumentException("WsListener must not be null");
        }
        if (builder.serverUrl == null) {
            throw new IllegalArgumentException("serverUrl must not be null");
        }
        this.listener = builder.listener;
        this.serverUrl = builder.serverUrl;
        this.draft = builder.draft;
        this.connectTimeout = builder.connectTimeout;
        this.httpHeaders = builder.httpHeaders;
        this.pingInterval = builder.pingInterval;
        this.wsKey = builder.wsKey;
        this.reconnectCount = builder.reconnectCount;
        this.reconnectInterval = builder.reconnectInterval;
        this.reConnectWhenNetworkAvailable = builder.reConnectWhenNetworkAvailable;
        setConnectionLostTimeout(pingInterval);
    }

    /**
     * WebSocket回调
     */
    private final IWebSocketListener listener;

    /**
     * 服务端地址
     */
    private final String serverUrl;

    /**
     * Websocket协议，默认6455
     */
    private final Draft draft;

    /**
     * 连接超时时间，默认值：0
     */
    private final int connectTimeout;

    /**
     * 初始化时设置的标识，不设置，自动使用默认WebSocket
     */
    private final String wsKey;

    /**
     * 心跳时间，单位秒，默认60。
     * 如果小于等于0，则关闭心跳功能。
     * 如果开启，若1.5倍间隔时间未收到服务端的pong响应，则自动断开连接
     */
    private final int pingInterval;

    /**
     * 重连次数，默认10，大于0开启重连功能
     */
    private final int reconnectCount;

    /**
     * 自动重连间隔, 单位毫秒，默认值1000
     */
    private final long reconnectInterval;

    /**
     * 重连任务
     */
    private ReconnectTask task;

    public ReconnectTask getTask() {
        return task;
    }

    public synchronized void runReconnectTask() {
        if (WsManager.getInstance().getTaskReconnectCount() >= reconnectCount) {
            WsLogUtil.e("已达到最大重连次数，如需重连请调用reset");
            return;
        }
        if (WsManager.getInstance().isReconnectTaskRun()){
            WsLogUtil.e("重连任务已正在运行");
            return;
        }
        ThreadUtils.cancel(task);
        task = new ReconnectTask(wsKey);
        task.execute();
    }

    /**
     * 网络可用时是否自动重连，默认值true
     */
    private final boolean reConnectWhenNetworkAvailable;

    private final Map<String, String> httpHeaders;

    public IWebSocketListener getListener() {
        return listener;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getWsKey() {
        return wsKey;
    }

    public int getPingInterval() {
        return pingInterval;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReconnectCount() {
        return reconnectCount;
    }

    public long getReconnectInterval() {
        return reconnectInterval;
    }

    public boolean isReConnectWhenNetworkAvailable() {
        return reConnectWhenNetworkAvailable;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    @Override
    public void send(String text) {
        super.send(text);
        listener.onSendMessage(this, text);
    }

    @Override
    public void send(byte[] data) {
        super.send(data);
        listener.onSendMessage(this, data);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        ThreadUtils.cancel(task);
        listener.onConnected(this);
    }

    @Override
    public void onMessage(String message) {
        listener.onMessage(this, message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        listener.onDisconnect(this, new DisConnectReason(code, reason, remote));
        runReconnectTask();
    }

    @Override
    public void onError(Exception ex) {
        listener.onError(this, ex);
    }

    @Override
    public void onWebsocketPing(WebSocket conn, Framedata frameData) {
        super.onWebsocketPing(conn, frameData);
        listener.onPing(this, frameData);
    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata frameData) {
        listener.onPong(this, frameData);
    }

    @Override
    public Draft getDraft() {
        return draft;
    }

    public static final class Builder {

        private String serverUrl;

        private IWebSocketListener listener;

        private String wsKey = DEFAULT_WEBSOCKET;

        private Draft draft = new Draft_6455();

        private int connectTimeout = 0;

        private int pingInterval = 60;

        private int reconnectCount = 10;

        private long reconnectInterval = 1000;

        private boolean reConnectWhenNetworkAvailable = true;

        private Map<String, String> httpHeaders = new HashMap<>();

        public Builder setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder setListener(IWebSocketListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder setDraft(Draft draft) {
            this.draft = draft;
            return this;
        }

        public Builder setWsKey(String wsKey) {
            this.wsKey = wsKey;
            return this;
        }

        public Builder setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder setPingInterval(int pingInterval) {
            this.pingInterval = pingInterval;
            return this;
        }

        public Builder setReconnectCount(int reconnectCount) {
            this.reconnectCount = reconnectCount;
            return this;
        }

        public Builder setReconnectInterval(long reconnectInterval) {
            this.reconnectInterval = reconnectInterval;
            return this;
        }

        public Builder setHttpHeaders(Map<String, String> httpHeaders) {
            this.httpHeaders = httpHeaders;
            return this;
        }

        public Builder setReConnectWhenNetworkAvailable(boolean reConnectWhenNetworkAvailable) {
            this.reConnectWhenNetworkAvailable = reConnectWhenNetworkAvailable;
            return this;
        }

        public WsClient build() {
            return new WsClient(URI.create(serverUrl), draft
                    , httpHeaders, connectTimeout, this);
        }
    }
}
