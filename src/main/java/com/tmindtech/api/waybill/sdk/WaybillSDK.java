package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.exception.AwesomeException;
import com.tmindtech.api.waybill.sdk.interceptor.HotSwitchInterceptor;
import com.tmindtech.api.waybill.sdk.interceptor.SignatureInterceptor;
import com.tmindtech.api.waybill.sdk.model.LabelInfo;
import com.tmindtech.api.waybill.sdk.model.Package;
import com.tmindtech.api.waybill.sdk.model.PrintResult;
import com.tmindtech.api.waybill.sdk.util.ImageStreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 这个 SDK 要判断当前仓库中, 各个本地服务是否可用, 如果一个本地服务不可用要自动切换使用另一个本地服务
 * 如果所有本地服务全部不可用则直接访问云端服务器(接口是一样的, 在内部实现切换)
 * 所有的一切都要在 SDK 内自动完成, 不需要外部干预
 */
public class WaybillSDK {
    private static final Logger log;
    private String accessKey;
    private String accessSecret;
    private List<URL> localServerAddressList;
    private URL cloudServerAddress;
    private PrintService printService;
    private PrintListener listener;

    static {
        BasicConfigurator.configure();
        log = LoggerFactory.getLogger(WaybillSDK.class);
    }

    /**
     * SDK 初始化时传入必要的参数(由调用者传入)
     *
     * @param accessKey              接口签名用Key, 向SDK提供方获取
     * @param accessSecret           接口签名用Secret, 向SDK提供方获取
     * @param cloudServerAddress     云端面单服务器地址
     * @param localServerAddressList 本地面单服务器地址列表
     * @return 初始化结果
     */
    public boolean init(String accessKey, String accessSecret,
                        String cloudServerAddress, List<String> localServerAddressList) {
        if (this.accessKey != null && this.accessSecret != null && this.cloudServerAddress != null) {
            return false;
        }
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
        try {
            this.cloudServerAddress = new URL(cloudServerAddress);
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
            return false;
        }
        List<URL> localServerAddresses = new ArrayList<>();
        for (String localServerAddress : localServerAddressList) {
            try {
                localServerAddresses.add(new URL(localServerAddress));
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
                return false;
            }
        }
        this.localServerAddressList = localServerAddresses;
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
        DocFlavor dof = DocFlavor.INPUT_STREAM.JPEG;
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
            throw new AwesomeException(Config.PrinterNotExist);
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
        List<String> uris;
        try {
            uris = getWaybillService().getLabelInfo(saleOrder).execute().body();
        } catch (IOException ex) {
            throw new AwesomeException(Config.OrderNotExist);
        }
        if (uris != null) {
            for (int i = 0; i < uris.size(); i++) {
                LabelInfo info = new LabelInfo(i, uris.size(), saleOrder, uris.get(i));
                try {
                    Boolean isReady = getPictureService().getOrderPictureByPath(uris.get(i)).execute().isSuccessful() ? Boolean.TRUE : Boolean.FALSE;
                    info.setIsReady(isReady);
                    labelInfos.add(info);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    info.setIsReady(Boolean.FALSE);
                    labelInfos.add(info);
                }
            }
        }
        return labelInfos;
    }

    /**
     * 通过唯一码获取面单图片
     *
     * @param uniqueCode 面单唯一码
     * @return 面单图片字节流
     */
    public InputStream getLabelImageByUniqueCode(String uniqueCode) {
        try {
            Response<ResponseBody> response = getPictureService().getOrderPictureByPath(uniqueCode).execute();
            if (response != null && response.isSuccessful()) {
                return response.body().byteStream();
            } else {
                throw new AwesomeException(Config.LabelNotReady);
            }
        } catch (IOException ex) {
            throw new AwesomeException(Config.LabelNotExist);
        }
    }

    /**
     * 分包 当存在一件多包的情况时，需要先进行分包操作，面单服务依据新的信息重新生成面单
     *
     * @param saleOrder    批次号
     * @param carrierCode  承运商编码，使用快递100的编码
     * @param packageCount 包裹数
     * @param packageList  货物信息
     * @param remark       运单备注（不超过20字）
     * @return 面单信息列表，兼容多包裹
     */
    public List<LabelInfo> splitPackage(String saleOrder, String carrierCode, Integer packageCount, List<Package> packageList, String remark) {
        List<LabelInfo> labelInfos = new ArrayList<>();
        List<String> uris;
        try {
            uris = getWaybillService().splitPackage(saleOrder, carrierCode, packageCount, remark, packageList).execute().body();
        } catch (IOException ex) {
            throw new AwesomeException(Config.OrderNotExist);
        }
        if (uris != null) {
            for (int i = 0; i < uris.size(); i++) {
                LabelInfo info = new LabelInfo(i, uris.size(), saleOrder, uris.get(i));
                try {
                    Boolean isReady = getPictureService().getOrderPictureByPath(uris.get(i)).execute().isSuccessful() ? Boolean.TRUE : Boolean.FALSE;
                    info.setIsReady(isReady);
                    labelInfos.add(info);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    info.setIsReady(Boolean.FALSE);
                    labelInfos.add(info);
                }
            }
        }
        return labelInfos;
    }

    /**
     * 通过唯一码请求打印面单. 支持批量打印, 并自动等待未生成的面单. 打印结果以异步回调方式进行通知
     *
     * @param uniqueCodeList 唯一码列表
     * @param printer        指定打印机（为null时，默认使用当前打印机）
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void printLabelByUniqueCode(List<String> uniqueCodeList, String printer) {
        PrintService currPrinter = printer == null ? printService : getPrinterByName(printer);
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(() -> {
            for (int i = 0; i < uniqueCodeList.size(); i++) {
                try {
                    Response<ResponseBody> response = getPictureService().getOrderPictureByPath(uniqueCodeList.get(i)).execute();
                    if (response.isSuccessful()) {
                        InputStream inputStream = ImageStreamUtil.convertImageStream2MatchPrinter(response.body().byteStream());
                        PrintResult printResult = printWaybill(currPrinter, inputStream);
                        LabelInfo labelInfo = new LabelInfo(i, uniqueCodeList.size(), null, uniqueCodeList.get(i));
                        labelInfo.setIsReady(Boolean.TRUE);
                        listener.onUniqueCodePrint(uniqueCodeList.get(i), printResult.isSuccess, labelInfo, printResult.code, printResult.result);
                    } else {
                        LabelInfo labelInfo = new LabelInfo(i, uniqueCodeList.size(), null, uniqueCodeList.get(i));
                        labelInfo.setIsReady(Boolean.FALSE);
                        listener.onUniqueCodePrint(uniqueCodeList.get(i), false, labelInfo, 0, "label not exists");
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    LabelInfo labelInfo = new LabelInfo(i, uniqueCodeList.size(), null, uniqueCodeList.get(i));
                    labelInfo.setIsReady(Boolean.FALSE);
                    listener.onUniqueCodePrint(uniqueCodeList.get(i), false, labelInfo, -1, "server unreachable");
                }
            }
        });
        executorService.shutdown();
    }

    /**
     * 通过批次号请求打印面单. 支持批量打印. 打印结果以异步回调方式进行通知
     *
     * @param saleOrderList 批次号列表
     * @param printer       指定打印机（为null时，默认使用当前打印机）
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void printLabelBySaleOrder(List<String> saleOrderList, String printer) {
        PrintService currPrinter = printer == null ? printService : getPrinterByName(printer);
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(() -> {
            for (int i = 0; i < saleOrderList.size(); i++) {
                String saleOrder = saleOrderList.get(i);
                try {
                    Response<List<String>> response = getWaybillService().getLabelInfo(saleOrder).execute();
                    if (response.isSuccessful()) {  //200
                        List<String> uniqueCodeList = response.body();
                        if (uniqueCodeList != null && uniqueCodeList.size() != 0) {
                            printWaybillByStream(currPrinter, uniqueCodeList, saleOrder);
                        }
                    } else {
                        if (response.code() == 404) {  //404
                            listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, null, -3, "resource not exists");
                        } else if (response.code() == 102) {  //102
                            ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
                            final CountDownLatch countDownLatch = new CountDownLatch(1);
                            final Map<String, Future> futures = new HashMap<>();
                            final AtomicBoolean flag = new AtomicBoolean(Boolean.FALSE);
                            Future future = executor.scheduleWithFixedDelay(() -> {
                                if (flag.get() && futures.get("sale_order") != null) {
                                    futures.get("sale_order").cancel(true);
                                    countDownLatch.countDown();
                                }
                                try {
                                    Response<List<String>> newResponse = getWaybillService().getLabelInfo(saleOrder).execute();
                                    if (newResponse.isSuccessful()) {
                                        List<String> uniqueCodeList = newResponse.body();
                                        if (uniqueCodeList != null && uniqueCodeList.size() != 0) {
                                            printWaybillByStream(currPrinter, uniqueCodeList, saleOrder);
                                        } else {
                                            listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, null, -3, "resource not exists");
                                        }
                                        flag.set(Boolean.TRUE);
                                    }
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, null, -1, "server unreachable");
                                    flag.set(Boolean.TRUE);
                                }
                            }, 0, 2, TimeUnit.SECONDS);
                            futures.put("sale_order", future);
                            try {
                                countDownLatch.await();
                            } catch (InterruptedException ex) {
                                log.info("countDownLatch await failure");
                                throw new AwesomeException(Config.ErrorEexcutorInterrupt);
                            }
                            executor.shutdown();
                        } else {  //500
                            listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, null, -1, "server unreachable");
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, null, -1, "server unreachable");
                }
            }
        });
        executorService.shutdown();
    }

    private void printWaybillByStream(PrintService printService, List<String> uniqueCodeList, String saleOrder) {
        for (int i = 0; i < uniqueCodeList.size(); i++) {
            try {
                Response<ResponseBody> pictureResponse = getPictureService().getOrderPictureByPath(uniqueCodeList.get(i)).execute();
                if (pictureResponse.isSuccessful()) {
                    PrintResult result = printWaybill(printService, pictureResponse.body().byteStream());
                    LabelInfo labelInfo = new LabelInfo(i, uniqueCodeList.size(), saleOrder, uniqueCodeList.get(i));
                    labelInfo.setIsReady(Boolean.TRUE);
                    listener.onSaleOrderPrint(saleOrder, result.isSuccess, labelInfo, result.code, result.result);
                } else {
                    LabelInfo labelInfo = new LabelInfo(i, uniqueCodeList.size(), saleOrder, uniqueCodeList.get(i));
                    labelInfo.setIsReady(Boolean.FALSE);
                    listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, labelInfo, -3, "resource not exists");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                LabelInfo labelInfo = new LabelInfo(i, uniqueCodeList.size(), saleOrder, uniqueCodeList.get(i));
                labelInfo.setIsReady(Boolean.FALSE);
                listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, labelInfo, -1, "server unreachable");
            }
        }
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
                    result.code = pje.getPrintEventType();
                    result.result = "print success, please wait for next print job";
                }
            });
            job.print(doc, pras);
        } catch (PrintException ex) {
            ex.printStackTrace();
            result.code = -2;
            result.isSuccess = Boolean.FALSE;
            result.result = "print failed, please check if the printer available";
        }
        return result;
    }

    @Getter(lazy = true)
    private final WaybillService waybillService = buildWaybillService(cloudServerAddress, localServerAddressList);

    private WaybillService buildWaybillService(URL cloudServerAddress, List<URL> localServerAddressList) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SignatureInterceptor(accessKey, accessSecret))
                .addInterceptor(new HotSwitchInterceptor(localServerAddressList))
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .build();

        return new Retrofit.Builder()
                .baseUrl(cloudServerAddress.toString())
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build()
                .create(WaybillService.class);
    }

    @Getter(lazy = true)
    private final PictureService pictureService = buildPictureService();

    private PictureService buildPictureService() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();

        return new Retrofit.Builder()
                .baseUrl(Constants.PICTURE_SERVER_URL)
                .client(okHttpClient)
                .build()
                .create(PictureService.class);
    }
}
