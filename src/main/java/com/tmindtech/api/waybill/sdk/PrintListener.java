package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.LabelInfo;

public interface PrintListener {

    void onUniqueCodePrint(String uniqueCode, Boolean isSuccess, LabelInfo labelInfo, Integer errorCode, String errorMessage);

    void onSaleOrderPrint(String saleOrder, Boolean isSuccess, LabelInfo labelInfo, Integer errorCode, String errorMessage);

}
