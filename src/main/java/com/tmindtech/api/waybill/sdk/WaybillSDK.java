package com.tmindtech.api.waybill.sdk;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.tmindtech.api.waybill.sdk.interceptor.SignatureInterceptor;
import com.tmindtech.api.waybill.sdk.model.Data;
import com.tmindtech.api.waybill.sdk.model.ImageData;
import com.tmindtech.api.waybill.sdk.model.LabelData;
import com.tmindtech.api.waybill.sdk.model.LabelInfo;
import com.tmindtech.api.waybill.sdk.model.Package;
import com.tmindtech.api.waybill.sdk.model.Payload;
import com.tmindtech.api.waybill.sdk.model.PrintResult;
import com.tmindtech.api.waybill.sdk.model.YXMessage;
import com.tmindtech.api.waybill.sdk.util.ImageStreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PrintQuality;
import javax.print.attribute.standard.PrinterName;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 这个 SDK 要判断当前仓库中, 各个本地服务是否可用, 如果一个本地服务不可用要自动切换使用另一个本地服务
 * 如果所有本地服务全部不可用则直接访问云端服务器(接口是一样的, 在内部实现切换)
 * 所有的一切都要在 SDK 内自动完成, 不需要外部干预
 */
public class WaybillSDK {
    //private static final Logger log;
    private String accessKey;
    private String accessSecret;
    private String serverAddress;
    private PrintService printService;
    private PrintListener listener;
    public ExecutorService executorService = Executors.newFixedThreadPool(1);

    private static final String SOURCE = "label_print";
    private static final String TARGET = "api";
    private static final String LABEL_ORDER_TOPIC = "logistics_label_address/find_by_sale_order";
    private static final String LABEL_ADDRESS_TOPIC = "logistics_label_address/get_label_image_by_uuid";
    private static final String LABEL_UUID_TOPIC = "logistics_label_address/find_by_uuid";
    private static final String SPLIT_ORDER_TOPIC = "relabel";

//    static {
//        BasicConfigurator.configure();
//        log = LoggerFactory.getLogger(WaybillSDK.class);
//    }

    /**
     * SDK 初始化时传入必要的参数(由调用者传入)
     *
     * @param accessKey     接口签名用Key, 向SDK提供方获取
     * @param accessSecret  接口签名用Secret, 向SDK提供方获取
     * @param serverAddress 面单服务器地址
     * @return 初始化结果
     */
    public boolean init(String accessKey, String accessSecret, String serverAddress) {
        if (accessKey == null || accessSecret == null || serverAddress == null) {
            return false;
        }
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
        this.serverAddress = serverAddress;
        this.printService = PrintServiceLookup.lookupDefaultPrintService();
        return true;
    }

    /**
     * 获得可用打印机名称列表
     *
     * @return 返回的所有可用打印机名称
     */
    public List<String> getPrinterList() {
        List<String> list = new ArrayList<>();
        DocFlavor dof = DocFlavor.INPUT_STREAM.PNG;
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(dof, null);
        if (printServices.length != 0) {
            Arrays.asList(printServices).forEach(item -> list.add(item.getName()));
        }
        return list;
    }

    /**
     * 设置当前打印机,如果没有设置，则printService为默认打印机
     *
     * @param name 要设置的指定打印机
     * @return true设置成功，false设置失败
     */
    public boolean setCurrentPrinter(String name) {
        DocFlavor dof = DocFlavor.INPUT_STREAM.PNG;
        HashAttributeSet hs = new HashAttributeSet();
        hs.add(new PrinterName(name, null));
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(dof, hs);
        if (printServices.length == 0) {
            return false;
        }
        this.printService = printServices[0];
        return true;
    }

    /**
     * 通过打印机名称获取指定名称
     *
     * @param name 指定打印机名称
     * @return 目标打印机名称
     */
    public PrintService getPrinterByName(String name) {
        DocFlavor dof = DocFlavor.INPUT_STREAM.PNG;
        HashAttributeSet hs = new HashAttributeSet();
        hs.add(new PrinterName(name, null));
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(dof, hs);
        if (printServices.length == 0) {
            throw new RuntimeException("PrinterNotSupport");
        } else {
            return printServices[0];
        }
    }

    /**
     * 设置打印结果监听, 接收打印结果异步回调
     *
     * @param listener 打印结果监听器
     */
    public void setPrintListener(PrintListener listener) {
        this.listener = listener;
    }

    /**
     * 获取面单信息，然后通过逻辑连接/唯一码获取到面单图片
     *
     * @param saleOrder 出库批次号
     * @return 面单信息列表，兼容多包裹
     */
    public List<LabelInfo> getLabelInfo(String saleOrder) {
        List<LabelInfo> labelInfos = new ArrayList<>();
        try {
            YXMessage message = new YXMessage(0, LABEL_ORDER_TOPIC, SOURCE, TARGET, "sign", saleOrder);
            Data data = getWaybillService().getLabelInfo(JSONObject.toJSONString(message)).execute().body();
            if (data != null) {
                if (data.data.isEmpty()) {
                    throw new RuntimeException("SaleOrderNotExist");
                }
                labelInfos = data.data;
            }
        } catch (IOException ex) {
            throw new RuntimeException("当前服务不可用");
        }
        return labelInfos;
    }

    /**
     * 通过唯一码获取面单图片
     *
     * @param uuidCode 面单唯一码
     * @return 面单图片字节流
     */
    public InputStream getLabelImageByUuidCode(String uuidCode) {
        try {
            YXMessage message = new YXMessage(0, LABEL_ADDRESS_TOPIC, SOURCE, TARGET, "sign", uuidCode);
            ImageData imageData = getPictureService().getOrderPictureByPath(JSONObject.toJSONString(message)).execute().body();
            if (imageData == null) {
                throw new RuntimeException("当前服务不可用");
            }
            if (imageData.code == 200) { //200
                return downloadImageStream(imageData.data);
            } else if (imageData.code == 404) { //404
                throw new RuntimeException("LabelNotExist");
            } else if (imageData.code == 102) {
                throw new RuntimeException("LabelNotReady");
            } else {
                throw new RuntimeException("当前服务不可用");
            }
        } catch (IOException ex) {
            throw new RuntimeException("当前服务不可用");
        }
    }

    /**
     * 分包 当存在一件多包的情况时，需要先进行分包操作，面单服务依据新的信息重新生成面单
     *
     * @param saleOrder    批次号
     * @param carrierCode  承运商编码，使用快递100的编码
     * @param packageCount 包裹数
     * @param packageList  货物信息
     * @return 面单信息列表，兼容多包裹
     */
    public List<LabelInfo> splitPackage(String saleOrder, String carrierCode, Number packageCount, List<Package> packageList) {
        List<LabelInfo> labelInfos = new ArrayList<>();
        try {
            Payload payload = new Payload(saleOrder, carrierCode, packageCount, packageList);

            YXMessage message = new YXMessage(0, SPLIT_ORDER_TOPIC, SOURCE, TARGET, "sign", JSONObject.toJSONString(payload));
            Data data = getWaybillService().splitPackage(JSONObject.toJSONString(message)).execute().body();
            if (data != null) {
                if (data.data.isEmpty()) {
                    throw new RuntimeException("saleOrderNotExist");
                }
                labelInfos = data.data;
            }
        } catch (IOException ex) {
            throw new RuntimeException("当前服务不可用");
        }
        return labelInfos;
    }

    /**
     * 通过唯一码请求打印面单. 支持批量打印, 并自动等待未生成的面单. 打印结果以异步回调方式进行通知
     *
     * @param uuidCodeList   唯一码列表
     * @param printer        指定打印机（为null时，默认使用当前打印机）
     * @param needAllSuccess 是否需要等待图片全部生成完再打印
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void printLabelByUuidCode(List<String> uuidCodeList, String printer, Boolean needAllSuccess) {
        PrintService currPrinter = printer == null ? printService : getPrinterByName(printer);
        if (Objects.isNull(currPrinter)) {
            return;
        }
        executorService.execute(() -> syncPrintLabelByUuidCode(uuidCodeList, currPrinter, needAllSuccess == null ? Boolean.FALSE : needAllSuccess));
    }

    private void syncPrintLabelByUuidCode(List<String> uuidCodeList, PrintService currPrinter, boolean needAllSuccess) {
        Map<String, String> map = new LinkedHashMap<>();
        if (needAllSuccess) {
            for (String uuidCode : uuidCodeList) {
                AtomicBoolean serverAvailable = new AtomicBoolean(Boolean.FALSE);
                while (!serverAvailable.get()) {
                    try {
                        YXMessage message = new YXMessage(0, LABEL_ADDRESS_TOPIC, SOURCE, TARGET, "sign", uuidCode);
                        Response<ImageData> response = getPictureService().getOrderPictureByPath(JSONObject.toJSONString(message)).execute();
                        if (response.body().code != 200) {
                            uuidCodeList.forEach(item -> {
                                if (uuidCode.equals(item)) {
                                    listener.onPrint(uuidCode, Boolean.FALSE, getLabelInfoByUuidCode(uuidCode), Constants.LABEL_NOT_READY, "label_not_ready");
                                } else {
                                    listener.onPrint(item, Boolean.FALSE, getLabelInfoByUuidCode(item), Constants.USER_CANCEL, "user_cancel");
                                }
                            });
                            return;
                        } else {
                            map.put(uuidCode, response.body().data);
                            serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
                        }
                    } catch (IOException ex) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            }
            Set<String> uuidCodes = map.keySet();
            for (String uuidCode : uuidCodes) {
                InputStream imageStream = downloadImageStream(map.get(uuidCode));
                InputStream inputStream = ImageStreamUtil.convertImageStream2MatchPrinter(imageStream, currPrinter);
                PrintResult printResult = printWaybill(currPrinter, inputStream);
                listener.onPrint(uuidCode, printResult.isSuccess, getLabelInfoByUuidCode(uuidCode), printResult.code, printResult.result);
            }
        } else {
            for (String uuidCode : uuidCodeList) {
                AtomicBoolean serverAvailable = new AtomicBoolean(Boolean.FALSE);
                while (!serverAvailable.get()) {
                    YXMessage message = new YXMessage(0, LABEL_ADDRESS_TOPIC, SOURCE, TARGET, "sign", uuidCode);
                    try {
                        Response<ImageData> response = getPictureService().getOrderPictureByPath(JSONObject.toJSONString(message)).execute();
                        if (response.body().code == 200) {
                            InputStream imageStream = downloadImageStream(response.body().data);
                            InputStream inputStream = ImageStreamUtil.convertImageStream2MatchPrinter(imageStream, currPrinter);
                            PrintResult printResult = printWaybill(currPrinter, inputStream);
                            listener.onPrint(uuidCode, printResult.isSuccess, getLabelInfoByUuidCode(uuidCode), printResult.code, printResult.result);
                        } else {
                            listener.onPrint(uuidCode, Boolean.FALSE, getLabelInfoByUuidCode(uuidCode), Constants.LABEL_NOT_READY, "label not ready");
                        }
                        serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
                    } catch (IOException ex) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            }
        }

//        for (String uuidCode : uuidCodeList) {
//            AtomicBoolean serverAvailable = new AtomicBoolean(Boolean.FALSE);
//            while (!serverAvailable.get()) {
//                LabelInfo info = null;
//                Response<LabelData> findResponse;
//                try {
//                    YXMessage message = new YXMessage(0, LABEL_UUID_TOPIC, SOURCE, TARGET, "sign", uuidCode);
//                    findResponse = getWaybillService().findPictureByPath(JSONObject.toJSONString(message)).execute();
//                    if (findResponse.isSuccessful()) {
//                        info = findResponse.body().data;
//                    }
//                    YXMessage message1 = new YXMessage(0, LABEL_ADDRESS_TOPIC, SOURCE, TARGET, "sign", uuidCode);
//                    Response<ImageData> response = getPictureService().getOrderPictureByPath(JSONObject.toJSONString(message1)).execute();
//                    if (response.body().code == 200) {
//                        InputStream inputStream = ImageStreamUtil.convertImageStream2MatchPrinter(new FileInputStream(response.body().data), currPrinter);
//                        PrintResult printResult = printWaybill(currPrinter, inputStream);
//                        listener.onPrint(uuidCode, printResult.isSuccess, info, printResult.code, printResult.result);
//                        serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
//                    } else if (response.body().code == 404) {
//                        listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.LABEL_NOT_EXIST, "label not exist");
//                        serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
//                    } else if (response.body().code == 102) {
//                        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
//                        final CountDownLatch countDownLatch = new CountDownLatch(1);
//                        final Map<String, Future> futures = new HashMap<>();
//                        final AtomicBoolean flag = new AtomicBoolean(Boolean.FALSE);
//                        Future future = executor.scheduleWithFixedDelay(() -> {
//                            if (flag.get() && futures.get("uuid_code") != null) {
//                                futures.get("uuid_code").cancel(true);
//                                countDownLatch.countDown();
//                            }
//                            try {
//                                Response<ImageData> newResponse = getPictureService().getOrderPictureByPath(uuidCode).execute();
//                                if (newResponse.body().code == 200) {
//                                    LabelInfo labelInfo = getWaybillService().findPictureByPath(uuidCode).execute().body().data;
//                                    InputStream inputStream = ImageStreamUtil.convertImageStream2MatchPrinter(new FileInputStream(newResponse.body().data), currPrinter);
//                                    PrintResult printResult = printWaybill(currPrinter, inputStream);
//                                    listener.onPrint(uuidCode, printResult.isSuccess, labelInfo, printResult.code, printResult.result);
//                                    flag.set(Boolean.TRUE);
//                                }
//                            } catch (IOException ignore) {
//                            }
//                        }, 0, 2, TimeUnit.SECONDS);
//                        futures.put("uuid_code", future);
//                        try {
//                            countDownLatch.await();
//                            serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
//                        } catch (InterruptedException ex) {
//                            listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.UNKNOWN, "unknown error");
//                        }
//                        executor.shutdown();
//                    } else {
//                        listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.NETWORK_ERROR, "server unreachable");
//                    }
//                } catch (IOException ex) {
//                    try {
//                        Thread.sleep(5000);
//                    } catch (InterruptedException ignore) {
//                    }
//                    listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.NETWORK_ERROR, "server unreachable");
//                }
//            }
//        }
    }

    /**
     * 通过批次号分包并请求打印面单，打印结果以异步回调方式进行通知
     *
     * @param saleOrder      批次号
     * @param carrierCode    承运商编码
     * @param packageCount   包裹数
     * @param packageList    货物信息
     * @param printer        指定打印机（为null时，默认使用当前打印机）
     * @param needAllSuccess 是否需要等待图片全部生成完再打印
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void splitPackageAndPrint(String saleOrder, String carrierCode, Number packageCount, List<Package> packageList, String printer, Boolean needAllSuccess) {
        executorService.execute(() -> {
            List<LabelInfo> labelInfoList = splitPackage(saleOrder, carrierCode, packageCount, packageList);
            List<String> uuidCodeList = new ArrayList<>();
            labelInfoList.forEach(labelInfo -> uuidCodeList.add(labelInfo.uuidCode));
            printLabelByUuidCode(uuidCodeList, printer, needAllSuccess);
        });
    }

    private PrintResult printWaybill(PrintService printService, InputStream inputStream) {
        PrintResult result = new PrintResult();
        DocFlavor dof = DocFlavor.INPUT_STREAM.PNG;
        HashPrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
        pras.add(OrientationRequested.PORTRAIT);
        pras.add(PrintQuality.HIGH);
        pras.add(new Copies(1));
        pras.add(MediaSizeName.ISO_A6);
        try {
            Doc doc = new SimpleDoc(inputStream, dof, null);
            DocPrintJob job = printService.createPrintJob();
            job.addPrintJobListener(new PrintJobAdapter() {
                @Override
                public void printJobNoMoreEvents(PrintJobEvent pje) {
                    result.isSuccess = Boolean.TRUE;
                    result.code = Constants.SUCCESS;
                    result.result = "print success, please wait for next print job";
                }
            });
            job.print(doc, pras);
        } catch (PrintException ex) {
            ex.printStackTrace();
            result.code = Constants.PRINTER_NOT_EXIST;
            result.isSuccess = Boolean.FALSE;
            result.result = "print failed, please check if the printer available";
        }
        return result;
    }

    private LabelInfo getLabelInfoByUuidCode(String uuidCode) {
        AtomicBoolean serverAvailable = new AtomicBoolean(Boolean.FALSE);
        LabelInfo labelInfo = new LabelInfo();
        while (!serverAvailable.get()) {
            YXMessage message = new YXMessage(0, LABEL_UUID_TOPIC, SOURCE, TARGET, "sign", uuidCode);
            Response<LabelData> response;
            try {
                response = getWaybillService().findPictureByPath(JSONObject.toJSONString(message)).execute();
                if (response.isSuccessful()) {
                    labelInfo = response.body().data;
                    serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
                } else {
                    throw new RuntimeException("labelNotExist");
                }
            } catch (IOException ex) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignore) {
                }
            }
        }
        return labelInfo;
    }

    private InputStream downloadImageStream(String imageUrl) {
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            RequestConfig config = RequestConfig.custom().setSocketTimeout(15000).setConnectTimeout(3000).build();
            HttpGet httpGet = new HttpGet(imageUrl);
            httpGet.setConfig(config);
            return client.execute(httpGet).getEntity().getContent();
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException("图片下载失败");
        } finally {
            try {
                client.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Getter(lazy = true)
    private final WaybillService waybillService = buildWaybillService(serverAddress);

    private WaybillService buildWaybillService(String serverAddress) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SignatureInterceptor(accessKey, accessSecret))
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .build();

        return new Retrofit.Builder()
                .baseUrl(serverAddress + "/request")
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()))
                .client(okHttpClient)
                .build()
                .create(WaybillService.class);
    }

    @Getter(lazy = true)
    private final PictureService pictureService = buildPictureService(serverAddress);

    private PictureService buildPictureService(String serverAddress) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SignatureInterceptor(accessKey, accessSecret))
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();

        return new Retrofit.Builder()
                .baseUrl(serverAddress + "/request")
                .client(okHttpClient)
                .build()
                .create(PictureService.class);
    }
}
