package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.LabelInfo;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 打印SDK接口方法测试
 */
public class WaybillSDKApplication implements PrintListener {

    public static void main(String[] args) {
        //初始化SDK
        WaybillSDK sdk = new WaybillSDK();
        boolean initResult = sdk.init("qQL346t327sNEhVSFVGEsIzmhAdq3u4b", "UmmZowSvZb0SNg0is6X8kx3SRJKxq6l6",
                "http://dev3.tmindtech.com:9031/");
        System.out.println("SDK初始化结果：" + initResult);

        //获取并输出所有的打印机名称
        List<String> printers = sdk.getPrinterList();
        System.out.println("所有打印机名称：");
        printers.forEach(System.out::println);

        //设置指定打印机
        boolean setPrinterResult = sdk.setCurrentPrinter(args[0]);
        System.out.println("设置指定打印机结果：" + setPrinterResult);

        //设置添加打印监听器
        sdk.setPrintListener(new WaybillSDKApplication());

        if ("getLabelInfo".equals(args[1])) {
            //通过批次号获取面单信息，然后通过逻辑连接/唯一码获取面单图片
            List<LabelInfo> infos = sdk.getLabelInfo(args[2]);
            System.out.println("通过批次号获取的所有面单信息和可读状态：");
            infos.forEach(info -> System.out.println(info.toString()));
        }


        //重新下单获取面单信息，然后通过逻辑连接/唯一码获取面单图片
//        List<LabelInfo> labelInfos = sdk.splitPackage("saleOrder2", "45124521", 3, null, "split");
//        System.out.println("重新下单获取所有面单信息和可读状态：");
//        labelInfos.forEach(labelInfo -> System.out.println(labelInfo.toString()));

        if ("getLabelImageByUniqueCode".equals(args[1])) {
            //通过唯一码获取面单图片流
            InputStream inputStream1 = sdk.getLabelImageByUuidCode(args[2]);
            System.out.println("指定的唯一码获取的流是否可用：");
            System.out.println(inputStream1 == null ? "图片流不可用" : "图片流可用");
        }

        if ("printLabelByUniqueCode".equals(args[1])) {
            //通过唯一码请求打印面单. 支持批量打印, 并自动等待未生成的面单
            List<String> logicUriList = new ArrayList<>();
            logicUriList.add(args[2]);
            logicUriList.add(args[3]);
            sdk.printLabelByUuidCode(logicUriList, args[4]);
        }

        if ("printLabelBySaleOrder".equals(args[1])) {
            //通过批次号请求打印面单. 支持批量打印. 打印结果以异步回调方式进行通知
            sdk.splitPackageAndPrint(args[2], null, 1, null, args[3]);
        }

        //打印任务结束，关闭线程池
        sdk.executorService.shutdown();
    }

    @Override
    public void onPrint(String uuidCode, Boolean isSuccess, LabelInfo labelInfo, Number errorCode, String errorMessage) {
        System.out.println("通过唯一码打印监听结果出来了：" + uuidCode + "," + isSuccess + "," + errorCode + "," + errorMessage);
        if (Objects.nonNull(labelInfo)) {
            System.out.println("labelInfo：" + labelInfo.toString());
        }
    }
}
