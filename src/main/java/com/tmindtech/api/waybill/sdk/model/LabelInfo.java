package com.tmindtech.api.waybill.sdk.model;

import com.google.gson.annotations.SerializedName;

public class LabelInfo {

    public Integer index; // 第几个面单，从0开始计数

    public Integer totalCount; // 分单总数

    public String saleOrder; // 出库批次号

    @SerializedName("uuid_code")
    public String uniqueCode; // 唯一码

    public Boolean isReady; // 面单是否生成完成

    @Override
    public String toString() {
        return "LabelInfo{" +
                "index=" + index +
                ", totalCount=" + totalCount +
                ", saleOrder='" + saleOrder + '\'' +
                ", uniqueCode='" + uniqueCode + '\'' +
                ", isReady=" + isReady +
                '}';
    }
}
