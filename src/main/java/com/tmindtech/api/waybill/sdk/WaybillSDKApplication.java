package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.LabelInfo;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/**
 * 打印SDK接口方法测试
 */
public class WaybillSDKApplication implements PrintListener {

    public static void main(String[] args) {
        WaybillSDK sdk = new WaybillSDK();
        Scanner scanner = new Scanner(System.in);
        //初始化SDK
        System.out.println("SDK初始化:");
        System.out.println("请输入accessKey:");
        String accessKey = scanner.next();
        System.out.println("请输入accessSecret:");
        String accessSecret = scanner.next();
        System.out.println("请输入serverAddress:");
        String serverAddress = scanner.next();
        if ("default".equals(serverAddress)) {
            serverAddress = "http://106.15.40.222:9043/";
        }
        boolean initResult = sdk.init(accessKey, accessSecret, serverAddress);
        System.out.println("SDK初始化结果：" + initResult);

        //获取并输出所有的打印机名称
        List<String> printers = sdk.getPrinterList();
        System.out.println("所有打印机名称：");
        for (int i = 1; i <= printers.size(); i++) {
            System.out.println(i + " : " + printers.get(i - 1));
        }

        //设置指定打印机
        System.out.println("设置当前打印机，请输入以上打印机名称所对应的序号：");
        int index = scanner.nextInt();
        boolean setPrinterResult = sdk.setCurrentPrinter(printers.get(index - 1));
        System.out.println("设置指定打印机结果：" + setPrinterResult);

        //设置添加打印监听器
        sdk.setPrintListener(new WaybillSDKApplication());

        while (true) {
            System.out.println("-----------------------------------------------------------------------");
            System.out.println(" 1 : 通过批次号saleOrder获取面单信息");
            System.out.println(" 2 : 分包重新下单并获取面单信息");
            System.out.println(" 3 : 通过唯一码uuidCode获取面单图片");
            System.out.println(" 4 : 通过唯一码uuidCode请求打印");
            System.out.println(" 5 : 批次号saleOrder分包请求打印");
            System.out.println(" 0 : 退出");
            System.out.println("-----------------------------------------------------------------------");
            int selectInpt = scanner.nextInt();
            if (0 == selectInpt) {
                break;
            } else if (1 == selectInpt) {
                //通过批次号获取面单信息，然后通过逻辑连接/唯一码获取面单图片
                System.out.println("请输入批次号：");
                List<LabelInfo> infos = sdk.getLabelInfo(scanner.next());
                System.out.println("通过批次号获取的所有面单信息和可读状态：");
                infos.forEach(info -> System.out.println(info.toString()));
            } else if (2 == selectInpt) {
                //重新下单获取面单信息，然后通过逻辑连接/唯一码获取面单图片
                System.out.println("请输入分包的批次号：");
                String saleOrder = scanner.next();
                List<LabelInfo> labelInfos = sdk.splitPackage(saleOrder, null, 1, null);
                System.out.println("重新下单获取所有面单信息和可读状态：");
                labelInfos.forEach(labelInfo -> System.out.println(labelInfo.toString()));
            } else if (3 == selectInpt) {
                //通过唯一码获取面单图片流
                System.out.println("请输入唯一码uuidCode：");
                InputStream inputStream1 = sdk.getLabelImageByUuidCode(scanner.next());
                System.out.println("指定的唯一码获取的流是否可用：");
                System.out.println(inputStream1 == null ? "图片流不可用" : "图片流可用");
            } else if (4 == selectInpt) {
                //通过唯一码请求打印面单. 支持批量打印, 并自动等待未生成的面单
                System.out.println("请输入唯一码uuidCode：");
                String uuidCode = scanner.next();
                System.out.println("所有打印机名称：");
                for (int i = 1; i <= printers.size(); i++) {
                    System.out.println(i + " : " + printers.get(i - 1));
                }
                System.out.println("请输入指定打印机序号：");
                int printer = scanner.nextInt();
                System.out.println("请输入needAllSuccess：");
                Boolean needAllSuccess = scanner.nextBoolean();
                List<String> logicUriList = new ArrayList<>();
                logicUriList.add(uuidCode);
                sdk.printLabelByUuidCode(logicUriList, printers.get(printer - 1), needAllSuccess);
            } else if (5 == selectInpt) {
                //通过批次号请求打印面单. 支持批量打印. 打印结果以异步回调方式进行通知
                System.out.println("请输入要打印的批次号：");
                String saleOrder = scanner.next();
                System.out.println("请输入指定的打印机：");
                String printer = scanner.next();
                System.out.println("请输入needAllSuccess：");
                Boolean needAllSuccess = scanner.nextBoolean();
                sdk.splitPackageAndPrint(saleOrder, null, 1, null, printer, needAllSuccess);
            } else {
                System.out.println("输入有误，请按上述提示输入！");
            }
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
