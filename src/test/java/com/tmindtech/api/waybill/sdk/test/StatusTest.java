package com.tmindtech.api.waybill.sdk.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tmindtech.api.waybill.sdk.Constants;
import com.tmindtech.api.waybill.sdk.WaybillSDK;
import com.tmindtech.api.waybill.sdk.model.StatusModel;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 测试用例, 按照规范, 类名要以 Test 结尾
 */
public class StatusTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusTest.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    public void getStatus() throws Exception {
        LOGGER.info("Start status test");
        //这里是瞎写的, 实际上要通过读取文件什么的来得到测试用的 key
        //.execute() 是同步方法, 会阻塞执行
        StatusModel statusModel = new WaybillSDK("123456", "123456", "HP LaserJet Professional M1213nf MFP", new String[]{})
                .getWaybillService(Constants.CLOUD_BASE_URL)
                .getStatus()
                .execute()
                .body();
        GSON.toJson(statusModel, System.out);
        System.out.println();
    }
}
