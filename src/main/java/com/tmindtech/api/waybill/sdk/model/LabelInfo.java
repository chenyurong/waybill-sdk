package com.tmindtech.api.waybill.sdk.model;

public class LabelInfo {

    public String saleOrder; // 出库批次号

    public Number seqNo; // 包裹的序号，例如 1,2,3

    public String uuidCode; // 唯一码

    public Integer pageCount;//图片中分开打印的面单数量

    public String data;//面单图片真实存储地址

    @Override
    public String toString() {
        return "LabelInfo{" +
                "saleOrder='" + saleOrder + '\'' +
                ", seqNo=" + seqNo +
                ", uuidCode='" + uuidCode + '\'' +
                ", pageCount=" + pageCount +
                '}';
    }
}
