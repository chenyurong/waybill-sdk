package com.tmindtech.api.waybill.sdk;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.tmindtech.api.waybill.sdk.interceptor.SignatureInterceptor;
import com.tmindtech.api.waybill.sdk.model.Data;
import com.tmindtech.api.waybill.sdk.model.ImageData;
import com.tmindtech.api.waybill.sdk.model.LabelData;
import com.tmindtech.api.waybill.sdk.model.LabelInfo;
import com.tmindtech.api.waybill.sdk.model.Package;
import com.tmindtech.api.waybill.sdk.model.PrintResult;
import com.tmindtech.api.waybill.sdk.util.ImageStreamUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
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
            Data data = getWaybillService().getLabelInfo(saleOrder).execute().body();
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
            ImageData imageData = getPictureService().getOrderPictureByPath(uuidCode).execute().body();
            if (imageData == null) {
                throw new RuntimeException("当前服务不可用");
            }
            if (imageData.code == 200) { //200
                // todo 通过图片地址获取图片流
                return new FileInputStream(imageData.data);
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
            Data data = getWaybillService().splitPackage(saleOrder, carrierCode, packageCount, packageList).execute().body();
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
     * @param uuidCodeList 唯一码列表
     * @param printer      指定打印机（为null时，默认使用当前打印机）
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void printLabelByUuidCode(List<String> uuidCodeList, String printer) {
        PrintService currPrinter = printer == null ? printService : getPrinterByName(printer);
        if (Objects.isNull(currPrinter)) {
            return;
        }
        executorService.execute(() -> syncPrintLabelByUuidCode(uuidCodeList, currPrinter));
    }

    private void syncPrintLabelByUuidCode(List<String> uuidCodeList, PrintService currPrinter) {
        for (String uuidCode : uuidCodeList) {
            AtomicBoolean serverAvailable = new AtomicBoolean(Boolean.FALSE);
            while (!serverAvailable.get()) {
                LabelInfo info = null;
                Response<LabelData> findResponse;
                try {
                    findResponse = getWaybillService().findPictureByPath(uuidCode).execute();
                    if (findResponse.isSuccessful()) {
                        info = findResponse.body().data;
                    }
                    Response<ImageData> response = getPictureService().getOrderPictureByPath(uuidCode).execute();
                    if (response.body().code == 200) {
                        // todo 通过data图片地址获取图片流
                        InputStream inputStream = ImageStreamUtil.convertImageStream2MatchPrinter(new FileInputStream(response.body().data), currPrinter);
                        PrintResult printResult = printWaybill(currPrinter, inputStream);
                        listener.onPrint(uuidCode, printResult.isSuccess, info, printResult.code, printResult.result);
                        serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
                    } else if (response.body().code == 404) {
                        listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.LABEL_NOT_EXIST, "label not exist");
                        serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
                    } else if (response.body().code == 102) {
                        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
                        final CountDownLatch countDownLatch = new CountDownLatch(1);
                        final Map<String, Future> futures = new HashMap<>();
                        final AtomicBoolean flag = new AtomicBoolean(Boolean.FALSE);
                        Future future = executor.scheduleWithFixedDelay(() -> {
                            if (flag.get() && futures.get("uuid_code") != null) {
                                futures.get("uuid_code").cancel(true);
                                countDownLatch.countDown();
                            }
                            try {
                                Response<ImageData> newResponse = getPictureService().getOrderPictureByPath(uuidCode).execute();
                                if (newResponse.body().code == 200) {
                                    LabelInfo labelInfo = getWaybillService().findPictureByPath(uuidCode).execute().body().data;
                                    // todo 通过data图片地址获取图片流
                                    InputStream inputStream = ImageStreamUtil.convertImageStream2MatchPrinter(new FileInputStream(newResponse.body().data), currPrinter);
                                    PrintResult printResult = printWaybill(currPrinter, inputStream);
                                    listener.onPrint(uuidCode, printResult.isSuccess, labelInfo, printResult.code, printResult.result);
                                    flag.set(Boolean.TRUE);
                                }
                            } catch (IOException ignore) {
                            }
                        }, 0, 2, TimeUnit.SECONDS);
                        futures.put("uuid_code", future);
                        try {
                            countDownLatch.await();
                            serverAvailable.compareAndSet(Boolean.FALSE, Boolean.TRUE);
                        } catch (InterruptedException ex) {
                            listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.UNKNOWN, "unknown error");
                        }
                        executor.shutdown();
                    } else {
                        listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.NETWORK_ERROR, "server unreachable");
                    }
                } catch (IOException ex) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignore) {
                    }
                    listener.onPrint(uuidCode, Boolean.FALSE, info, Constants.NETWORK_ERROR, "server unreachable");
                }
            }
        }
    }

    /**
     * 通过批次号分包并请求打印面单，打印结果以异步回调方式进行通知
     *
     * @param saleOrder    批次号
     * @param carrierCode  承运商编码
     * @param packageCount 包裹数
     * @param packageList  货物信息
     * @param printer      指定打印机（为null时，默认使用当前打印机）
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void splitPackageAndPrint(String saleOrder, String carrierCode, Number packageCount, List<Package> packageList, String printer) {
        executorService.execute(() -> {
            List<LabelInfo> labelInfoList = splitPackage(saleOrder, carrierCode, packageCount, packageList);
            List<String> uuidCodeList = new ArrayList<>();
            labelInfoList.forEach(labelInfo -> uuidCodeList.add(labelInfo.uuidCode));
            printLabelByUuidCode(uuidCodeList, printer);
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
                .baseUrl(serverAddress)
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
                .baseUrl(serverAddress)
                .client(okHttpClient)
                .build()
                .create(PictureService.class);
    }
}
