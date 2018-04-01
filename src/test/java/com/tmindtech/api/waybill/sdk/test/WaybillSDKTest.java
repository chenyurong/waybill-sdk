package com.tmindtech.api.waybill.sdk.test;

import com.tmindtech.api.waybill.sdk.PrintListener;
import com.tmindtech.api.waybill.sdk.WaybillSDK;
import com.tmindtech.api.waybill.sdk.WaybillSDKApplication;
import com.tmindtech.api.waybill.sdk.model.LabelInfo;
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
        assert initResult : "SDK初始化失败";

        //获取并输出所有的打印机名称
        List<String> printers = sdk.getPrinterList();
        System.out.println("所有打印机名称：");
        printers.forEach(System.out::println);

        //设置指定打印机
        boolean setPrinterResult = sdk.setCurrentPrinter("HPRT HLP106S-UE");
        assert setPrinterResult : "设置指定打印机失败";

        //设置添加打印监听器
        sdk.setPrintListener(new WaybillSDKApplication());

        //通过批次号获取面单信息，然后通过逻辑连接/唯一码获取面单图片
        List<LabelInfo> infos = sdk.getLabelInfo("saleOrder");
        System.out.println("通过批次号获取的所有面单信息和可读状态：");
        infos.forEach(info -> System.out.println(info.toString()));

        //通过逻辑链接获取面单图片流
        InputStream inputStream1 = sdk.getLabelImageByUniqueCode("11.png");
        try {
            System.out.println("指定的逻辑地址获取的流是否可用：");
            System.out.println(inputStream1.available());
        } catch (IOException ex) {
            System.out.println("测试2的逻辑地址：应该能用，不报错显示！");
        }
        //通过逻辑链接获取面单图片流
        InputStream inputStream2 = sdk.getLabelImageByUniqueCode("miandan.jpg");
        try {
            System.out.println("指定的逻辑地址获取的流是否可用：");
            System.out.println(inputStream2.available());
        } catch (IOException ex) {
            System.out.println("测试1的逻辑地址：应该报错显示！");
        }

        //通过逻辑链接请求打印面单. 支持批量打印, 并自动等待未生成的面单
        List<String> logicUriList = new ArrayList<>();
        logicUriList.add("72dpi.png");
        sdk.printLabelByUniqueCode(logicUriList, "HP LaserJet Professional M1213nf MFP");

        //通过批次号请求打印面单. 支持批量打印. 打印结果以异步回调方式进行通知
        List<String> saleOrderList = new ArrayList<>();
        saleOrderList.add("batch1");
        sdk.printLabelBySaleOrder(saleOrderList, null);
    }

    @Override
    public void onUniqueCodePrint(String uniqueCode, Boolean isSuccess, LabelInfo labelInfo, Integer errorCode, String errorMessage) {
        System.out.println("通过唯一码打印监听结果出来了：" + uniqueCode + "," + isSuccess + "," + errorCode + "," + errorMessage);
        if (labelInfo != null) {
            System.out.println("labelInfo：" + labelInfo.toString());
        }
    }

    @Override
    public void onSaleOrderPrint(String saleOrder, Boolean isSuccess, LabelInfo labelInfo, Integer errorCode, String errorMessage) {
        System.out.println("通过批次号打印监听结果出来了：" + saleOrder + "," + isSuccess + "," + errorCode + "," + errorMessage);
        if (labelInfo != null) {
            System.out.println("labelInfo：" + labelInfo.toString());
        }
    }
}
