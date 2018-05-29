package com.tmindtech.api.waybill.sdk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.tmindtech.api.waybill.sdk.model.LabelInfo;
import com.tmindtech.api.waybill.sdk.model.Package;
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
        if ("cloudServer".equals(serverAddress)) {
            serverAddress = "http://logistics-gateway.test.you.163.com/labelservice/";
        }
        System.out.println("请输入localAddress:");
        String localAddress = scanner.next();
        if ("localServer".equals(localAddress)) {
            localAddress = "http://47.100.64.170/";
        }
        if ("null".equals(localAddress)) {
            localAddress = null;
        }
        List<String> localAddresses = new ArrayList<>(1);
        localAddresses.add(localAddress);
        boolean initResult = sdk.init(accessKey, accessSecret, serverAddress, localAddresses);
        System.out.println("SDK初始化结果：" + initResult);

        //获取并输出所有的打印机名称
        List<String> printers = sdk.getPrinterList();
        System.out.println("所有打印机名称：");
        for (int i = 1; i <= printers.size(); i++) {
            System.out.println(i + " : " + printers.get(i - 1));
        }

        //设置指定打印机
        System.out.println("设置当前打印机，请输入以上打印机名称所对应的序号：");
        int index;
        try {
            index = scanner.nextInt();
        } catch (Exception ex) {
            System.out.println("输入的格式错误，请输入数字！");
            return;
        }
        if (index > printers.size()) {
            System.out.println("输入数字过大，请输入以上提示的数字！");
            return;
        }
        boolean setPrinterResult = sdk.setCurrentPrinter(printers.get(index - 1));
        System.out.println("设置指定打印机结果：" + setPrinterResult);

        //设置添加打印监听器
        sdk.setPrintListener(new WaybillSDKApplication());

        //设置包裹信息
        String str = "{\"packageList\":[{ \"seqNo\":1,\"weight\":20.2,\"volumeLong\":30.1,\"volumeWidth\":20.1,"
                + "\"volumeHeight\": 10.2,\"volume\":15,\"cargoList\":[{\"skuId\":\"1122aaabb\",\"count\": 3},"
                + "{\"skuId\": \"1122a2aabb\",\"count\":3}]},{\"seqNo\":2,\"weight\":20.2,\"volumeLong\":30.1,"
                + "\"volumeWidth\": 20.1,\"volumeHeight\":10.2,\"volume\":15,\"cargoList\":[{\"skuId\":\"1122aaabb\","
                + "\"count\":3},{\"skuId\": \"1122a2aabb\",\"count\":3}]},{\"seqNo\": 3,\"weight\": 20.2,\n"
                + "\"volumeLong\":30.1,\"volumeWidth\":20.1,\"volumeHeight\":10.2,\"volume\":15,"
                + "\"cargoList\":[{\"skuId\":\"1122aaabb\",\"count\":3},{\"skuId\":\"1122a2aabb\",\"count\": 3}]}]}";
        JSONObject object = JSONObject.parseObject(str);
        String packages = object.getString("packageList");
        ArrayList<Package> list = JSON.parseObject(packages, new TypeReference<ArrayList<Package>>() {
        });

        while (true) {
            System.out.println("-----------------------------------------------------------------------");
            System.out.println(" 1 : 通过批次号saleOrder获取面单信息");
            System.out.println(" 2 : 分包重新下单并获取面单信息");
            System.out.println(" 3 : 通过唯一码uuidCode获取面单图片");
            System.out.println(" 4 : 通过唯一码uuidCode请求打印");
            System.out.println(" 5 : 批次号saleOrder分包请求打印");
            System.out.println(" 0 : 退出");
            System.out.println("-----------------------------------------------------------------------");
            int selectInpt;
            try {
                selectInpt = scanner.nextInt();
            } catch (Exception ex) {
                System.out.println("输入的格式错误，请输入数字！");
                break;
            }
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
                List<LabelInfo> labelInfos = sdk.splitPackage(saleOrder, "jd", 3, list);
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
                sdk.printLabelByUuidCode(logicUriList, printers.get(printer - 1), needAllSuccess, 0);
            } else if (5 == selectInpt) {
                //通过批次号请求打印面单. 支持批量打印. 打印结果以异步回调方式进行通知
                System.out.println("请输入要打印的批次号：");
                String saleOrder = scanner.next();
                System.out.println("所有打印机名称：");
                for (int i = 1; i <= printers.size(); i++) {
                    System.out.println(i + " : " + printers.get(i - 1));
                }
                System.out.println("请输入指定打印机序号：");
                int printer = scanner.nextInt();
                System.out.println("请输入needAllSuccess：");
                Boolean needAllSuccess = scanner.nextBoolean();
                sdk.splitPackageAndPrint(saleOrder, "jd", 3, list,
                        printers.get(printer - 1), needAllSuccess, 30000);
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
