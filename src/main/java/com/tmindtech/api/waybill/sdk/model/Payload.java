package com.tmindtech.api.waybill.sdk.model;

import java.util.List;

public class Payload {
    private String saleOrder;
    private String carrierCode;
    private Number packageCount;
    private List<Package> packageList;

    public Payload(String saleOrder, String carrierCode, Number packageCount, List<Package> packageList) {
        this.saleOrder = saleOrder;
        this.carrierCode = carrierCode;
        this.packageCount = packageCount;
        this.packageList = packageList;
    }
}
