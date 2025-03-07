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

    private final String wsKey;
    private int reconnectCount;
    private final long reconnectInterval;
    private int count = 1;

    public ReconnectTask(String wsKey) {
        this.wsKey = wsKey;
        // 重置全局重连次数
        reconnectCount = WsManager.getInstance().getWsClient(wsKey).getReconnectCount();
        reconnectInterval = WsManager.getInstance().getWsClient(wsKey).getReconnectInterval();
    }

    @Override
    public Void doInBackground() {
        if (WsManager.getInstance().getWsClient(wsKey).isOpen()) {
            ThreadUtils.cancel(this);
            return null;
        }
        WsLogUtil.e("执行第" + count + "次重连");
        WsManager.getInstance().setTaskReconnectCount(count);
        WsManager.getInstance().safeConnect(WsManager.getInstance().getWsClient(wsKey));
        // 每次执行任务，重连次数递减，直到为0不再发起重连
        reconnectCount--;
        count++;
        return null;
    }

    @Override
    public void onSuccess(Void result) {
        if (reconnectCount == 0) {
            ThreadUtils.cancel(this);
        }
    }

    @Override
    public void onCancel() {
        WsLogUtil.e("重连任务执行完毕");
        WsManager.getInstance().setReconnectTaskRun(false);
    }

    public void execute() {
        if (WsManager.getInstance().isReconnectTaskRun()) {
            WsLogUtil.e("重连任务已执行");
            return;
        }
        if (!WsManager.getInstance().isNetworkAvailable()) {
            WsLogUtil.e("网络不可用, 不执行重连");
            return;
        }
        if (WsManager.getInstance().getWsClient(wsKey).isOpen()) {
            WsLogUtil.e("Socket已连接");
            return;
        }
        WsManager.getInstance().setReconnectTaskRun(true);
        ThreadUtils.executeByIoAtFixRate(this, reconnectInterval, TimeUnit.SECONDS);
    }
}
