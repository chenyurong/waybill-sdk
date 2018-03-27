package com.tmindtech.api.waybill.sdk;

public interface PrintListener {

    void onLogicUriPrint(String logicUri, Boolean isSuccess, Number errorCode, String errorMessage);

    void onSaleOrderPrint(String saleOrder, Boolean isSuccess, Number totalCount, Number index, String logicUri, Number errorCode, String errorMessage);

}
