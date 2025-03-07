package com.eurigo.websocketlib;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

import com.eurigo.websocketlib.util.AppUtils;
import com.eurigo.websocketlib.util.ThreadUtils;
import com.eurigo.websocketlib.util.WsLogUtil;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eurigo
 * Created on 2022/3/29 9:58
 * desc   : WebSocket管理器
 */
public class WsManager {

    /**
     * 所有WebSocket的合集
     */
    private final ConcurrentHashMap<String, WsClient> clientMap = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, WsClient> getClientMap() {
        return clientMap;
    }

    public static final String DEFAULT_WEBSOCKET = "DEFAULT_WEBSOCKET";
    public static final String NO_INIT = "没有初始化";

    private WebSocketServer webSocketServer;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isNetworkAvailable;

    private boolean isReconnectTaskRun = false;

    private int attempt = 0;

    private AtomicInteger taskReconnectCount = new AtomicInteger(0);

    private ReconnectGuardianTask guardianTask;

    /**
     * 重连保护进程的间隔，单位秒，默认60
     */
    private int guardianTaskInterval = 60;

    /**
     * 设置重连保护的间隔并启动保护任务
     *
     * @param guardianTaskInterval 保护检测间隔，单位秒，要求大于0且大于【2* reconnectCount * reconnectInterval】
     */
    public void startGuardianTaskInterval(int guardianTaskInterval) {
        this.guardianTaskInterval = guardianTaskInterval;
        ThreadUtils.cancel(guardianTask);
        guardianTask = new ReconnectGuardianTask();
        guardianTask.execute();

    }

    public int getGuardianTaskInterval() {
        return guardianTaskInterval;
    }

    public WebSocketServer getWebSocketServer() {
        return webSocketServer;
    }

    public synchronized void startWsServer(InetSocketAddress address, IWebSocketServerListener listener) {
        attempt = 0;
        webSocketServer = new WebSocketServer(address) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                listener.onWsOpen(conn, handshake);
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                listener.onWsClose(conn, code, reason, remote);
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                listener.onWsMessage(conn, message);
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                if (ex instanceof BindException) {
                    attempt++;
                    WsLogUtil.e("端口被占用, 尝试端口：" + (address.getPort() + attempt));
                }
                listener.onWsError(conn, ex);
            }

            @Override
            public void onStart() {
                listener.onWsStart(webSocketServer);
            }
        };
        webSocketServer.setReuseAddr(true);
        webSocketServer.start();
        // 添加JVM关闭钩子，当应用退出时，关闭WebSocket服务
        Runtime.getRuntime().addShutdownHook(new Thread(() -> WsManager.getInstance().stopWsServer()));
    }

    public synchronized void stopWsServer() {
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (InterruptedException e) {
                WsLogUtil.e(e.getMessage());
            }
            webSocketServer = null;
        }
    }

    /**
     * 设置重连次数
     */
    public void setTaskReconnectCount(int taskReconnectCount) {
        this.taskReconnectCount = new AtomicInteger(taskReconnectCount);
    }

    /**
     * 获取重连次数
     */
    public int getTaskReconnectCount() {
        return taskReconnectCount.get();
    }

    /**
     * 重置重连次数
     */
    public synchronized void resetTaskReconnectCount() {
        taskReconnectCount = new AtomicInteger(0);
    }

    public synchronized void setReconnectTaskRun(boolean reconnectTaskRun) {
        isReconnectTaskRun = reconnectTaskRun;
    }

    public synchronized boolean isReconnectTaskRun() {
        return isReconnectTaskRun;
    }

    /**
     * 获取连接池中最大的连接次数
     */
    public int getMaxReconnectCount() {
        int tempCount = 0;
        for (WsClient ws : clientMap.values()) {
            if (ws.getReconnectCount() > tempCount) {
                tempCount = ws.getReconnectCount();
            }
        }
        return tempCount;
    }

    public long getMaxReconnectInterval() {
        long tempInterval = 0;
        for (WsClient ws : clientMap.values()) {
            if (ws.getReconnectInterval() > tempInterval) {
                tempInterval = ws.getReconnectInterval();
            }
        }
        return tempInterval;
    }

    public WsManager() {
    }

    public static WsManager getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private static class SingletonHelper {
        private final static WsManager INSTANCE = new WsManager();
    }

    public boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }

    public void updateNetworkAvailable(Network network) {
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
        isNetworkAvailable = networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /**
     * 注册网络变化监听广播，网络由不可用变为可用时会重新连接 WebSocket
     * 调用后会立即触发一次OnReceive
     */
    public void registerNetworkChangedCallback() {
        if (networkCallback != null) {
            WsLogUtil.e("网络状态监听已注册");
            return;
        }
        if (!checkPermission()) {
            WsLogUtil.e("未获取到网络状态权限，广播监听器无法注册");
            return;
        }
        connectivityManager = (ConnectivityManager) AppUtils
                .getInstance()
                .getApp()
                .getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateNetworkAvailable(network);
                for (WsClient ws : WsManager.getInstance().getClientMap().values()) {
                    if (ws.isReConnectWhenNetworkAvailable() && !ws.isOpen() && !WsManager.getInstance().isReconnectTaskRun()) {
                        ws.runReconnectTask();
                    }
                }
                WsLogUtil.e("网络状态：" + isNetworkAvailable);
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateNetworkAvailable(network);
                WsLogUtil.e("网络状态：" + isNetworkAvailable);
            }
        };
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
        // 添加JVM关闭钩子，当应用退出时，关闭WebSocket服务
        Runtime.getRuntime().addShutdownHook(new Thread(() -> WsManager.getInstance().destroy()));
    }

    /**
     * 解除网络状态广播
     */
    private void unRegisterNetworkChangedCallback() {
        if (networkCallback == null) {
            WsLogUtil.d("网络状态广播未注册");
            return;
        }
        connectivityManager.unregisterNetworkCallback(networkCallback);
        networkCallback = null;
    }

    /**
     * 判断是否有网络权限{@link Manifest.permission#ACCESS_NETWORK_STATE}
     */
    private boolean checkPermission() {
        Context context = AppUtils.getInstance().getApp().getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PackageManager.PERMISSION_GRANTED == context.getPackageManager()
                    .checkPermission(ACCESS_NETWORK_STATE, context.getPackageName());
        }
        return true;
    }

    private void addClient(WsClient wsClient) {
        // 移除旧的WebSocket，再重新添加
        if (clientMap.containsKey(wsClient.getWsKey())) {
            WsClient oldClient = clientMap.get(wsClient.getWsKey());
            if (oldClient != null) {
                oldClient.closeConnection(-1, "addClient, close old");
            } else {
                WsLogUtil.e("Tried to close old connection, but it was null");
            }
        }
        clientMap.put(wsClient.getWsKey(), wsClient);
    }

    /**
     * 是否关闭日志输出，默认开启
     *
     * @param isClose 是否关闭
     */
    public WsManager closeLog(boolean isClose) {
        WsLogUtil.closeLog(isClose);
        return this;
    }

    /**
     * 获取默认的WebSocket
     */
    public WsManager init(WsClient wsClient) {
        addClient(wsClient);
        return this;
    }

    /**
     * 开始执行连接，每个WebSocket都会创建对应的重连任务
     */
    public void start() {
        registerNetworkChangedCallback();
        try {
            for (WsClient ws : clientMap.values()) {
                if (ws.isOpen()) {
                    WsLogUtil.e("请勿重复连接, key = " + ws.getWsKey());
                    continue;
                }
                if (ws.isClosed()) {
                    ws = reCreateClient(ws);
                    clientMap.put(ws.getWsKey(), ws);
                    ws.connect();
                } else {
                    ws.connectBlocking();
                }
            }
        } catch (Exception e) {
            WsLogUtil.e(e.getMessage());
        }
    }

    public void safeConnect(WsClient ws) {
        if (ws.isOpen()) {
            WsLogUtil.e("请勿重复连接, key = " + ws.getWsKey());
            return;
        }
        if (ws.isClosed()) {
            ws = reCreateClient(ws);
            clientMap.put(ws.getWsKey(), ws);
        }
        try {
            ws.connect();
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("WebSocketClient objects are not reuseable")) {
                ws = reCreateClient(ws);
                clientMap.put(ws.getWsKey(), ws);
                ws.connect();
            }
        } catch (Exception e) {
            WsLogUtil.e(e.getMessage());
        }
    }

    private WsClient reCreateClient(WsClient oldWsClient) {
        return new WsClient.Builder()
                .setServerUrl(oldWsClient.getServerUrl())
                .setWsKey(oldWsClient.getWsKey())
                .setPingInterval(oldWsClient.getPingInterval())
                .setDraft(oldWsClient.getDraft())
                .setHttpHeaders(oldWsClient.getHttpHeaders())
                .setConnectTimeout(oldWsClient.getConnectTimeout())
                .setReconnectCount(oldWsClient.getReconnectCount())
                .setReconnectInterval(oldWsClient.getReconnectInterval())
                .setReConnectWhenNetworkAvailable(oldWsClient.isReConnectWhenNetworkAvailable())
                .setListener(oldWsClient.getListener())
                .build();
    }

    public WsClient getDefault() {
        return getWsClient(DEFAULT_WEBSOCKET);
    }

    public WsClient getWsClient(String wsKey) {
        if (!clientMap.containsKey(wsKey)) {
            WsLogUtil.e(NO_INIT + wsKey);
            return null;
        }
        return clientMap.get(wsKey);
    }

    /**
     * 使用默认WebSocket发送信息
     *
     * @param message 消息
     */
    public void send(String message) {
        send(DEFAULT_WEBSOCKET, message);
    }

    /**
     * 使用指定的WebSocket发送信息
     *
     * @param wsKey   webSocket Key
     * @param message 消息
     */
    public void send(String wsKey, String message) {
        getWsClient(wsKey).send(message);
    }

    /**
     * 使用默认WebSocket发送ping
     */
    public void sendPing() {
        send(DEFAULT_WEBSOCKET);
    }

    /**
     * 使用指定的WebSocket发送ping
     *
     * @param wsKey webSocket Key
     */
    public void sendPing(String wsKey) {
        getWsClient(wsKey).sendPing();
    }

    /**
     * @return 默认WebSocket是否连接
     */
    public boolean isConnected() {
        if (getDefault() == null) {
            return false;
        }
        return isConnected(DEFAULT_WEBSOCKET);
    }

    /**
     * @param wsKey wsKey
     * @return WebSocket是否连接
     */
    public boolean isConnected(String wsKey) {
        if (getWsClient(wsKey) == null) {
            return false;
        }
        return getWsClient(wsKey).isOpen();
    }

    /**
     * 断开默认WebSocket连接
     */
    public void disConnect() {
        disConnect(DEFAULT_WEBSOCKET);
    }

    /**
     * 断开指定WebSocket
     *
     * @param wsKey wsKey
     */
    public void disConnect(String wsKey) {
        getWsClient(wsKey).close();
    }

    /**
     * 销毁资源, 销毁后Websocket需要重新初始化
     */
    public void destroy() {
        // 解除广播
        unRegisterNetworkChangedCallback();
        if (webSocketServer != null){
            webSocketServer = null;
        }
        // 关闭连接
        for (WsClient ws : clientMap.values()) {
            if (!ws.isFlushAndClose()) {
                ws.closeConnection(-1, "destroy");
            }
        }
        clientMap.clear();
    }

}
