package com.tmindtech.api.waybill.sdk.model;

public class LabelInfo {

    public String saleOrder; // 出库批次号

    public Number seqNo; // 包裹的序号，例如 1,2,3

    public String uuidCode; // 唯一码

    @Override
    public String toString() {
        return "LabelInfo{" +
                "saleOrder='" + saleOrder + '\'' +
                ", seqNo=" + seqNo +
                ", uuidCode='" + uuidCode + '\'' +
                '}';
    }
}
