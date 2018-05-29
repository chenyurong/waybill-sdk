package com.tmindtech.api.waybill.sdk.model;

import java.util.List;

public class Payload {
    public String saleOrder;
    public String carrierCode;
    public Number pkgCnt;
    public List<Package> packageList;

    public Payload(String saleOrder, String carrierCode, Number pkgCnt, List<Package> packageList) {
        this.saleOrder = saleOrder;
        this.carrierCode = carrierCode;
        this.pkgCnt = pkgCnt;
        this.packageList = packageList;
    }
}
