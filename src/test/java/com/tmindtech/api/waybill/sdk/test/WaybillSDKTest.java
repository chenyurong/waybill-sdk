package com.tmindtech.api.waybill.sdk.test;

import com.tmindtech.api.waybill.sdk.PrintListener;
import com.tmindtech.api.waybill.sdk.WaybillSDK;
import com.tmindtech.api.waybill.sdk.model.LogicUriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class WaybillSDKTest implements PrintListener {

    @Test
    public void printOrdersByBatch() {
        //初始化SDK
        WaybillSDK sdk = new WaybillSDK();
        List<String> addresses = new ArrayList<>();
        addresses.add("http://127.0.0.1:8088/tmind/v1/");
        addresses.add("http://127.0.0.1:8089/tmind/v1/");
        boolean initResult = sdk.init("testKey", "testSecret",
                "http://localhost:8080/tmind/v1/", addresses);
        System.out.println("SDK初始化结果：" + initResult);

        //获取并输出所有的打印机名称
        List<String> printers = sdk.getPrinterList();
        System.out.println("所有打印机名称：");
        printers.forEach(System.out::println);

        //设置指定打印机
        boolean setPrinterResult = sdk.setCurrentPrinter("HP LaserJet Professional M1213nf MFP");
        System.out.println("设置指定打印机结果：" + setPrinterResult);

        //设置添加打印监听器
        sdk.setPrintListener(WaybillSDKTest.this);

        //通过批次号获取面单逻辑地址列表（兼容一件多单）
        List<LogicUriInfo> infos = sdk.getWaybillAddress("saleOrder");
        System.out.println("通过批次号获取的所有逻辑地址和可读状态：");
        infos.forEach(info -> System.out.println("逻辑地址为：" + info.logicUri + ",可用状态为：" + info.isReady));

        //通过逻辑链接获取面单图片流
        InputStream inputStream1 = sdk.getWaybillImage("2.jpg");
        try {
            System.out.println("指定的逻辑地址获取的流是否可用：");
            System.out.println(inputStream1.available());
        } catch (IOException ex) {
            System.out.println("测试2的逻辑地址：应该能用，不报错显示！");
        }
//        //通过逻辑链接获取面单图片流
//        InputStream inputStream2 = sdk.getWaybillImage("1.jpg");
//        try {
//            System.out.println("指定的逻辑地址获取的流是否可用：");
//            System.out.println(inputStream2.available());
//        } catch (IOException ex) {
//            System.out.println("测试1的逻辑地址：应该报错显示！");
//        }

        //通过逻辑链接请求打印面单. 支持批量打印, 并自动等待未生成的面单
        List<String> logicUriList = new ArrayList<>();
        logicUriList.add("2.jpg");
        logicUriList.add("1.jpg");
        logicUriList.add("huida.jpg");

        sdk.printWaybillByLogicUri(logicUriList, 1, 0,
                0, 210, 297);


        //通过批次号请求打印面单. 支持批量打印. 打印结果以异步回调方式进行通知
        List<String> saleOrderList = new ArrayList<>();
        saleOrderList.add("batch1");
        saleOrderList.add("batch2");
        sdk.printWaybillBySaleOrder(saleOrderList, 1, 0, 0, 210, 297);
    }

    @Override
    public void onLogicUriPrint(String logicUri, Boolean isSuccess, Number errorCode, String errorMessage) {
        System.out.println("逻辑地址打印监听结果来了：" + logicUri + "," + isSuccess + "," + errorCode + "," + errorMessage);
    }

    @Override
    public void onSaleOrderPrint(String saleOrder, Boolean isSuccess, Number totalCount, Number index, String logicUri, Number errorCode, String errorMessage) {
        System.out.println("批次号打印监听结果来了：" + saleOrder + "," + isSuccess + "," + totalCount + "," + index + "," + logicUri + "," + errorCode + "," + errorMessage);
    }
}
