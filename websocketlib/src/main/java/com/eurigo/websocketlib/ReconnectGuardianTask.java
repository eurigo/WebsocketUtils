package com.eurigo.websocketlib;

import com.eurigo.websocketlib.util.ThreadUtils;

import java.util.concurrent.TimeUnit;

/**
 * @author eurigo
 * Created on 2025/3/7 08:19
 * desc   : WebSocket守护任务
 */
public class ReconnectGuardianTask extends ThreadUtils.SimpleTask<Void> {

    @Override
    public Void doInBackground() {
        WsManager.getInstance().start();
        return null;
    }

    @Override
    public void onSuccess(Void result) {

    }

    public void execute(){
        long reconnectInterval = WsManager.getInstance().getGuardianTaskInterval();
        if (reconnectInterval <= 0) {
            throw new IllegalArgumentException("reconnectInterval must be greater than 0");
        }
        if (reconnectInterval < WsManager.getInstance().getMaxReconnectCount() * WsManager.getInstance().getMaxReconnectInterval() * 2) {
            throw new IllegalArgumentException("reconnectInterval must be greater than maxReconnectCount * maxReconnectInterval * 2");
        }
        ThreadUtils.executeByCachedAtFixRate(this, WsManager.getInstance().getGuardianTaskInterval(), TimeUnit.SECONDS);
    }
}
