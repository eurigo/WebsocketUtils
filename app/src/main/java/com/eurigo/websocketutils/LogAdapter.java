package com.eurigo.websocketutils;

import com.blankj.utilcode.util.TimeUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eurigo
 * Created on 2021/6/30 10:38
 * desc   :
 */
public class LogAdapter extends BaseQuickAdapter<String, BaseViewHolder> {

    public LogAdapter(@Nullable List<String> data) {
        super(R.layout.item_log, data);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder helper, String s) {
        helper.setText(R.id.tv_item_log, s);
    }

    public void addDataAndScroll(@NotNull String data, boolean isClient) {
        String regex = isClient ? "客户端：" : "服务端收到：";
        addData(TimeUtils.getNowString() + "\n" + regex + data);
        getRecyclerView().scrollToPosition(getData().size() - 1);
    }
}
