package com.tmindtech.api.waybill.sdk.model;

import com.google.gson.annotations.SerializedName;

/**
 * 这是一个模型, 对应服务器将返回的 JSON
 * 使用 GsonFormat 插件来自动生成
 */
public class StatusModel {
    /**
     * status : UP
     */

    @SerializedName("status")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
