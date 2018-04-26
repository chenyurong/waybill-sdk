package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.LabelInfo;

public interface PrintListener {

    void onPrint(String uuidCode, Boolean isSuccess, LabelInfo labelInfo, Number errorCode, String errorMessage);

}
