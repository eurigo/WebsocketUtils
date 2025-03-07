package com.eurigo.websocketutils;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NetworkUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.eurigo.websocketlib.DisConnectReason;
import com.eurigo.websocketlib.IWebSocketListener;
import com.eurigo.websocketlib.IWebSocketServerListener;
import com.eurigo.websocketlib.WsClient;
import com.eurigo.websocketlib.WsManager;
import com.eurigo.websocketutils.databinding.ActivityMainBinding;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;

import java.util.ArrayList;

/**
 * @author Eurigo
 * Created on 2022/3/28 10:35
 * desc   :
 */
public class MainActivity extends ComponentActivity implements View.OnClickListener
        , IWebSocketListener, IWebSocketServerListener {

    private LogAdapter mAdapter;

    private String ipAddress;
    private static final int PORT = 8800;
    private static final String REGEX = "ws:/";

    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        initView();
        ipAddress = NetworkUtils.getIpAddressByWifi();
//        WsManager.getInstance().startWsServer(PORT, this);
//        connectWebSocket("ws://".concat(ipAddress).concat(":" + PORT));
        WsManager.getInstance().registerNetworkChangedCallback();
    }

    private void initView() {
        mAdapter = new LogAdapter(new ArrayList<>());
        mBinding.rcvApLog.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mBinding.rcvApLog.setLayoutManager(new LinearLayoutManager(this));
        mBinding.rcvApLog.setAdapter(mAdapter);
        mBinding.btnConnect.setOnClickListener(this);
        mBinding.btnClose.setOnClickListener(this);
        mBinding.btnSend.setOnClickListener(this);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        if (view == mBinding.btnConnect) {
            connectWebSocket(getEditText(mBinding.etAddress));
            return;
        }
        if (view == mBinding.btnClose) {
            WsManager.getInstance().disConnect();
            return;
        }
        if (view == mBinding.btnSend) {
            String msg = getEditText(mBinding.etMessage);
            if (TextUtils.isEmpty(msg)) {
                ToastUtils.showShort("请输入消息");
                return;
            }
            if (TextUtils.equals("reset", mBinding.etMessage.getText().toString().trim())) {
                WsManager.getInstance().resetTaskReconnectCount();
                return;
            }
            WsManager.getInstance().send(getEditText(mBinding.etMessage));
        }
    }

    private String getEditText(EditText editText) {
        return editText.getText().toString().trim();
    }

    /**
     * 连接WebSocket
     */
    private void connectWebSocket(String address) {
        if (TextUtils.isEmpty(ipAddress)) {
            ToastUtils.showShort("请先连接WIFI");
            return;
        }
        mBinding.etAddress.setText(address);
        // 构造一个默认WebSocket客户端
        WsClient wsClient = new WsClient.Builder()
                .setServerUrl(address)
                .setListener(this)
                .setReconnectInterval(1)
                .setReconnectCount(10)
                .setPingInterval(30)
                .build();
        // 初始化并启动连接
        WsManager.getInstance()
                .init(wsClient)
                .closeLog(false)
                .start();
        // 启动3分钟的重连保护
        WsManager.getInstance().startGuardianTaskInterval(60*3);

        WsManager.getInstance().startWsServer(8800, new IWebSocketServerListener() {
            @Override
            public void onWsOpen(WebSocket conn, ClientHandshake handshake) {

            }

            @Override
            public void onWsClose(WebSocket conn, int code, String reason, boolean remote) {

            }

            @Override
            public void onWsMessage(WebSocket conn, String message) {

            }

            @Override
            public void onWsError(WebSocket conn, Exception ex) {

            }

            @Override
            public void onWsStart() {

            }
        });
    }

    @Override
    public void onWsOpen(WebSocket conn, ClientHandshake handshake) {
        LogUtils.e("服务端日志", "服务器已启动，地址:" + conn.getLocalSocketAddress().toString());
    }

    @Override
    public void onWsClose(WebSocket conn, int code, String reason, boolean remote) {
        LogUtils.e("服务端日志", conn + "已关闭"
                , new DisConnectReason(code, reason, remote).toString());
    }

    @Override
    public void onWsMessage(WebSocket conn, String message) {
        runOnUiThread(() -> mAdapter.addDataAndScroll(message, false));
    }

    @Override
    public void onWsError(WebSocket conn, Exception ex) {
        LogUtils.e("服务端日志", "异常", ex);
        runOnUiThread(() -> mAdapter.addDataAndScroll(ex.getMessage(), false));
    }

    @Override
    public void onWsStart() {
        LogUtils.e("服务端日志", "服务器已启动", "地址：" + ipAddress + ":" + PORT);
    }

    /**
     * 客户端回调start
     */
    @Override
    public void onConnected(WsClient client) {
        runOnUiThread(() -> {
            mBinding.etAddress.setText(REGEX.concat(client.getRemoteSocketAddress().toString()));
            mAdapter.addDataAndScroll(client.getLocalSocketAddress().toString().replace("/", "").concat("  连接成功"), true);
            mBinding.btnConnect.setEnabled(false);
            mBinding.btnClose.setEnabled(true);
        });
    }

    @Override
    public void onClosing(WsClient client, DisConnectReason reason) {
        runOnUiThread(() -> mAdapter.addDataAndScroll("连接断开中...", true));
    }

    @Override
    public void onDisconnect(WsClient client, DisConnectReason reason) {
        runOnUiThread(() -> {
            mAdapter.addDataAndScroll("连接已断开", true);
            mBinding.btnConnect.setEnabled(true);
            mBinding.btnClose.setEnabled(false);
        });
    }

    @Override
    public void onError(WsClient webSocketClient, Exception ex) {
        LogUtils.e("客户端日志", "连接失败", ex.getMessage());
        runOnUiThread(() -> mAdapter.addDataAndScroll("连接失败：" + ex.getCause(), true));
    }

    @Override
    public void onMessage(WsClient webSocketClient, String message) {
        runOnUiThread(() -> {
            mAdapter.addDataAndScroll(message, true);
        });
    }

    @Override
    public void onPing(WsClient webSocketClient, Framedata frameData) {
        LogUtils.e("客户端日志", "收到Ping");
    }

    @Override
    public void onPong(WsClient webSocketClient, Framedata frameData) {
        LogUtils.e("客户端日志", "收到Pong");
    }

    @Override
    public void onSendMessage(WsClient client, String message) {
        LogUtils.e("客户端日志", "发送消息", message);
    }
}
