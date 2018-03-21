package com.tmindtech.api.waybill.sdk.test;

import com.tmindtech.api.waybill.sdk.WaybillSDK;
import java.util.List;
import org.junit.Test;

public class WaybillSDKTest {

    @Test
    public void printOrdersByBatch() {
        //初始化打印机
        WaybillSDK sdk = new WaybillSDK("testKey", "testSecret",
                "HP LaserJet Professional M1213nf MFP");

        //获取并输出所有的打印机名称
        List<String> printers = sdk.getAllPrintersName();
        printers.forEach(System.out::println);

        //根据批次号打印面单
        sdk.printOrderByBatchNumber("testBatch", 1,
                0, 0, 210, 297);
    }

}
