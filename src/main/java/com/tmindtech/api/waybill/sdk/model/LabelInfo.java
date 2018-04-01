package com.tmindtech.api.waybill.sdk.model;

public class LabelInfo {

    private Integer index; // 第几个面单，从0开始计数

    private Integer totalCount; // 分单总数

    private String saleOrder; // 出库批次号

    private String uniqueCode; // 唯一码

    private Boolean isReady; // 面单是否生成完成

    public LabelInfo() {
    }

    public LabelInfo(Integer index, Integer totalCount, String saleOrder, String uniqueCode) {
        this.index = index;
        this.totalCount = totalCount;
        this.saleOrder = saleOrder;
        this.uniqueCode = uniqueCode;
    }

    public void setIsReady(Boolean isReady) {
        this.isReady = isReady;
    }

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
