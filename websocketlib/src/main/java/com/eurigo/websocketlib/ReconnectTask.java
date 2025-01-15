package com.eurigo.websocketlib;

import com.eurigo.websocketlib.util.ThreadUtils;
import com.eurigo.websocketlib.util.WsLogUtil;

import java.util.concurrent.TimeUnit;

/**
 * @author Eurigo
 * Created on 2022/3/30 15:07
 * desc   :
 */
public class ReconnectTask extends ThreadUtils.SimpleTask<Void> {

    private final WsClient wsClient;
    private int reconnectCount;
    private final long reconnectInterval;

    private int count = 1;

    public ReconnectTask(WsClient wsClient) {
        this.wsClient = wsClient;
        reconnectCount = wsClient.getReconnectCount();
        reconnectInterval = wsClient.getReconnectInterval();
    }

    @Override
    public Void doInBackground() throws Throwable {
        WsLogUtil.e("执行第" + count + "次重连");
        if (wsClient.isOpen()) {
            cancel();
            return null;
        }
        wsClient.reconnectBlocking();
        // 每次执行任务，重连次数递减，直到为0不再发起重连
        reconnectCount--;
        count++;
        return null;
    }

    @Override
    public void onSuccess(Void result) {
        if (reconnectCount == 0) {
            cancel();
        }
    }

    @Override
    public void onCancel() {
        super.onCancel();
        WsManager.getInstance().setReconnectTaskRun(false);
        WsLogUtil.e("重连任务执行完毕");
    }

    public void execute() {
        if (WsManager.getInstance().isReconnectTaskRun()) {
            WsLogUtil.e("重连任务正在执行中");
            return;
        }
        if (!WsManager.getInstance().isNetworkAvailable()) {
            WsLogUtil.e("网络不可用, 不执行重连");
            cancel();
            return;
        }
        if (wsClient.isOpen()) {
            WsLogUtil.e("已连接成功");
            cancel();
            return;
        }
        WsManager.getInstance().setReconnectTaskRun(true);
        ThreadUtils.executeByCachedAtFixRate(this, reconnectInterval, TimeUnit.SECONDS);
    }
}
