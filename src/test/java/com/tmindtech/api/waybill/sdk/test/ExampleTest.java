package com.tmindtech.api.waybill.sdk.test;

import com.tmindtech.api.waybill.sdk.WaybillSDK;
import org.junit.Test;

public class ExampleTest {
    @Test
    public void getCatlog() throws Exception {
        new WaybillSDK("123456", "123456", "HP LaserJet Professional M1213nf MFP")
                .getWaybillService()
                .getCatlog(1, true)
                .execute();
    }
}
