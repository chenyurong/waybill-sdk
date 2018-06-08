package com.tmindtech.api.waybill.sdk;

import com.alibaba.fastjson.JSONObject;
import com.tmindtech.api.waybill.sdk.interceptor.SignatureInterceptor;
import com.tmindtech.api.waybill.sdk.model.Data;
import com.tmindtech.api.waybill.sdk.model.ImageData;
import com.tmindtech.api.waybill.sdk.model.LabelData;
import com.tmindtech.api.waybill.sdk.model.LabelInfo;
import com.tmindtech.api.waybill.sdk.model.Package;
import com.tmindtech.api.waybill.sdk.model.Payload;
import com.tmindtech.api.waybill.sdk.model.PrintLog;
import com.tmindtech.api.waybill.sdk.model.PrintStreamInfo;
import com.tmindtech.api.waybill.sdk.model.YXMessage;
import com.tmindtech.api.waybill.sdk.util.ImageStreamUtil;
import com.tmindtech.api.waybill.sdk.util.InputStreamCacher;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
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
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PrintQuality;
import javax.print.attribute.standard.PrinterName;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import lombok.Getter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.io.IOUtils;
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
    public ExecutorService serverExecutorService = Executors.newSingleThreadExecutor();

    private static final String SOURCE = "label_print";
    private static final String TARGET = "api";
    private static final String LABEL_ORDER_TOPIC = "logistics_label_address/find_by_sale_order";
    private static final String LABEL_ADDRESS_TOPIC = "logistics_label_address/get_label_image_by_uuid";
    private static final String LABEL_UUID_TOPIC = "logistics_label_address/find_by_uuid";
    private static final String SPLIT_ORDER_TOPIC = "relabel";
    private static final String LABEL_PRINT_LOG = "local_print_log";

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
    public boolean init(String accessKey, String accessSecret, String serverAddress, List<String> localAddresses) {
        if (accessKey == null || accessSecret == null || serverAddress == null) {
            return false;
        }
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
        this.serverAddress = serverAddress;
        this.printService = PrintServiceLookup.lookupDefaultPrintService();

        if (localAddresses != null && localAddresses.size() > 0) {
            //定时获取当前最优服务器，若所有本地服务器down机，则使用云端服务器
            getBestServerAddress(localAddresses);
        }

        return true;
    }

    private void getBestServerAddress(List<String> urls) {
        serverExecutorService.execute(() -> {
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleWithFixedDelay(() -> {
                long responseTime = -1;
                for (String url : urls) {
                    try {
                        long startTime = System.currentTimeMillis();
                        Response<ResponseBody> response = buildBestHealthServer(url).getBestHealthServer().execute();
                        if (response.isSuccessful()) {
                            String content = IOUtils.toString(response.body().byteStream(), StandardCharsets.UTF_8);
                            if ("ok".equals(content)) {
                                long endTime = System.currentTimeMillis();
                                if (responseTime == -1) {
                                    responseTime = endTime - startTime;
                                    serverAddress = url;
                                    System.out.println("First connect time : " + responseTime + "milliseconds!");
                                } else {
                                    if (endTime - startTime < responseTime) {
                                        responseTime = endTime - startTime;
                                        serverAddress = url;
                                    }
                                }
                            }
                        }
                    } catch (IOException ignore) {
                    }
                }
            }, 0, 20, TimeUnit.SECONDS);
        });
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
            YXMessage message = new YXMessage(UUID.randomUUID().toString(), 0,
                    LABEL_ORDER_TOPIC, SOURCE, TARGET, "", saleOrder);
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                    JSONObject.toJSONString(message));
            Data data = getWaybillService().getLabelInfo(body).execute().body();
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
            YXMessage message = new YXMessage(UUID.randomUUID().toString(), 0,
                    LABEL_ADDRESS_TOPIC, SOURCE, TARGET, "", uuidCode);
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                    JSONObject.toJSONString(message));
            ImageData imageData = getWaybillService().getOrderPictureByPath(body).execute().body();
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
    public List<LabelInfo> splitPackage(String saleOrder, String carrierCode,
                                        Number packageCount, List<Package> packageList) {
        List<LabelInfo> labelInfos = new ArrayList<>();
        try {
            Payload payload = new Payload(saleOrder, carrierCode, packageCount, packageList);

            YXMessage message = new YXMessage(UUID.randomUUID().toString(), 0,
                    SPLIT_ORDER_TOPIC, SOURCE, TARGET, "", payload);
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                    JSONObject.toJSONString(message));
            Data data = getWaybillService().splitPackage(body).execute().body();
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
     * @param uuidCodeList    唯一码列表
     * @param printer         指定打印机（为null时，默认使用当前打印机）
     * @param needAllSuccess  是否需要等待图片全部生成完再打印
     * @param getImageTimeout 获取面单图片超时等待(毫秒计)
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void printLabelByUuidCode(List<String> uuidCodeList, String printer,
                                     Boolean needAllSuccess, Integer getImageTimeout) {
        getImageTimeout = getImageTimeout == null ? 0 : getImageTimeout;
        final int timeout = getImageTimeout;
        PrintService currPrinter = printer == null ? printService : getPrinterByName(printer);
        if (Objects.isNull(currPrinter)) {
            return;
        }
        executorService.execute(() -> syncPrintLabelByUuidCode(uuidCodeList, currPrinter,
                needAllSuccess == null ? Boolean.FALSE : needAllSuccess, timeout));
    }

    private void syncPrintLabelByUuidCode(List<String> uuidCodeList, PrintService currPrinter,
                                          boolean needAllSuccess, int getImageTimeout) {
        long initialTime = System.currentTimeMillis();
        Map<String, LabelInfo> map = new LinkedHashMap<>();
        if (needAllSuccess) {
            for (String uuidCode : uuidCodeList) {
                YXMessage message = new YXMessage(UUID.randomUUID().toString(),
                        0, LABEL_ADDRESS_TOPIC, SOURCE, TARGET, "", uuidCode);
                RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                        JSONObject.toJSONString(message));
                Response<ImageData> response;
                try {
                    response = getWaybillService().getOrderPictureByPath(body).execute();
                } catch (IOException ex) {
                    throw new RuntimeException("server error");
                }
                if (response.body().code != 200) {
                    uuidCodeList.forEach(item -> {
                        long lastTime = System.currentTimeMillis();
                        if (uuidCode.equals(item)) {
                            listener.onPrint(uuidCode, Boolean.FALSE, getLabelInfoByUuidCode(uuidCode),
                                    Constants.LABEL_NOT_READY, "label_not_ready");
                        } else {
                            listener.onPrint(item, Boolean.FALSE, getLabelInfoByUuidCode(item),
                                    Constants.USER_CANCEL, "user_cancel");
                        }
                        savePrintResultLog(uuidCode, lastTime - initialTime, "PRINT_FAIL");
                    });
                    return;
                } else {
                    LabelInfo labelInfo = getLabelInfoByUuidCode(uuidCode);
                    labelInfo.data = response.body().data;
                    map.put(uuidCode, labelInfo);
                }
            }
            Set<String> uuidCodes = map.keySet();
            for (String uuidCode : uuidCodes) {
                LabelInfo labelInfo = map.get(uuidCode);
                long startTime = System.currentTimeMillis();
                InputStream imageStream = downloadImageStream(labelInfo.data);
                PrintStreamInfo streamInfo = ImageStreamUtil.convertImageStream2MatchPrinter(imageStream,
                        currPrinter, labelInfo.pageCount);
                if (Objects.isNull(streamInfo)) {
                    throw new RuntimeException("imageStream convert failure");
                }

                //pageCount大于1，说明该面单需要分多张图片打印
                if (labelInfo.pageCount > 1) {
                    InputStreamCacher cacher = new InputStreamCacher(streamInfo.inputStream);
                    for (int i = 0; i < labelInfo.pageCount; i++) {
                        boolean result = printWaybill(currPrinter, cacher.getInputStream(), uuidCode, i,
                                labelInfo.pageCount, streamInfo.width, streamInfo.height);
                        if (!result) {
                            throw new RuntimeException("print error");
                        }
                    }
                }
                if (labelInfo.pageCount == 1) {
                    boolean result = printWaybill(currPrinter, streamInfo.inputStream, uuidCode, 0,
                            1, streamInfo.width, streamInfo.height);
                    if (!result) {
                        throw new RuntimeException("print error");
                    }
                }
                long endTime = System.currentTimeMillis();
                savePrintResultLog(uuidCode, endTime - startTime, "PRINT_SUCCESS");
            }
        } else {
            for (String uuidCode : uuidCodeList) {
                AtomicInteger printCount = new AtomicInteger(0);
                while (printCount.get() != 1) {
                    long startTime = System.currentTimeMillis();
                    YXMessage message = new YXMessage(UUID.randomUUID().toString(), 0,
                            LABEL_ADDRESS_TOPIC, SOURCE, TARGET, "", uuidCode);
                    RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                            JSONObject.toJSONString(message));
                    Response<ImageData> response;
                    try {
                        response = getWaybillService().getOrderPictureByPath(body).execute();
                    } catch (IOException ex) {
                        throw new RuntimeException("server error");
                    }
                    if (response.body().code == 200) {
                        LabelInfo labelInfo = getLabelInfoByUuidCode(uuidCode);
                        InputStream imageStream = downloadImageStream(response.body().data);
                        PrintStreamInfo streamInfo = ImageStreamUtil.convertImageStream2MatchPrinter(imageStream,
                                currPrinter, labelInfo.pageCount);
                        if (Objects.isNull(streamInfo)) {
                            throw new RuntimeException("imageStream convert failure");
                        }

                        //pageCount大于1，说明该面单需要分多张图片打印
                        boolean flag = true;
                        if (labelInfo.pageCount > 1) {
                            InputStreamCacher cacher = new InputStreamCacher(streamInfo.inputStream);
                            for (int i = 0; i < labelInfo.pageCount; i++) {
                                boolean result = printWaybill(currPrinter, cacher.getInputStream(), uuidCode, i,
                                        labelInfo.pageCount, streamInfo.width, streamInfo.height);
                                if (!result) {
                                    flag = false;
                                    break;
                                }
                            }
                        }
                        if (labelInfo.pageCount == 1) {
                            flag = printWaybill(currPrinter, streamInfo.inputStream, uuidCode, 0,
                                    1, streamInfo.width, streamInfo.height);
                        }
                        String logResult = flag ? "PRINT_SUCCESS" : "PRINT_FAIL";
                        long endTime = System.currentTimeMillis();
                        savePrintResultLog(uuidCode, endTime - startTime, logResult);
                        printCount.compareAndSet(0, 1);
                    } else {
                        if (printCount.get() == 2) {
                            long endTime = System.currentTimeMillis();
                            listener.onPrint(uuidCode, Boolean.FALSE, getLabelInfoByUuidCode(uuidCode),
                                    Constants.LABEL_NOT_READY, "label not ready");
                            savePrintResultLog(uuidCode, endTime - startTime, "PRINT_FAIL");
                            break;
                        }
                        try {
                            Thread.sleep(getImageTimeout);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        printCount.compareAndSet(0, 2);
                    }
                }
            }
        }
    }

    private void savePrintResultLog(String uuidCode, long printTime, String result) {
        PrintLog printLog = new PrintLog(Constants.DATE_FORMAT.format(new Date()), result,
                TimeUnit.MILLISECONDS.toSeconds(printTime), uuidCode);
        YXMessage logMessage = new YXMessage(UUID.randomUUID().toString(), 0,
                LABEL_PRINT_LOG, SOURCE, TARGET, "", JSONObject.toJSONString(printLog));
        RequestBody logBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                JSONObject.toJSONString(logMessage));
        try {
            Response<ResponseBody> logResponse = getWaybillService().savePrintResultLog(logBody).execute();
            if (!logResponse.isSuccessful()) {
                throw new RuntimeException("server error");
            }
        } catch (IOException ex) {
            throw new RuntimeException("server error");
        }
    }

    /**
     * 通过批次号分包并请求打印面单，打印结果以异步回调方式进行通知
     *
     * @param saleOrder       批次号
     * @param carrierCode     承运商编码
     * @param packageCount    包裹数
     * @param packageList     货物信息
     * @param printer         指定打印机（为null时，默认使用当前打印机）
     * @param needAllSuccess  是否需要等待图片全部生成完再打印
     * @param getImageTimeout 获取面单图片超时等待(毫秒计)
     * @return 如果打印宽度 (printerWidth) > 高度 (printerHeight) 会产生IllegalArgumentException异常
     */
    public void splitPackageAndPrint(String saleOrder, String carrierCode, Number packageCount,
                                     List<Package> packageList, String printer,
                                     Boolean needAllSuccess, Integer getImageTimeout) {
        getImageTimeout = getImageTimeout == null ? 30000 : getImageTimeout;
        final int timeout = getImageTimeout;
        executorService.execute(() -> {
            List<LabelInfo> labelInfoList = new ArrayList<>();
            AtomicInteger splitCount = new AtomicInteger(0);
            while (splitCount.get() != 1) {
                labelInfoList = splitPackage(saleOrder, carrierCode, packageCount, packageList);
                if (labelInfoList.size() > 0) {
                    splitCount.compareAndSet(0, 1);
                } else {
                    if (splitCount.get() == 2) {
                        throw new RuntimeException("LabelNotExist");
                    }
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    splitCount.compareAndSet(0, 2);
                }
            }
            List<String> uuidCodeList = new ArrayList<>();
            labelInfoList.forEach(labelInfo -> uuidCodeList.add(labelInfo.uuidCode));
            printLabelByUuidCode(uuidCodeList, printer, needAllSuccess, 0);
        });
    }

    private boolean printWaybill(PrintService printService, InputStream inputStream,
                                 String uuidCode, int index, int size, float width, float height) {
        InputStream printStream;
        try {
            BufferedImage image = ImageIO.read(inputStream);
            BufferedImage subImage = image.getSubimage(0, (int) (index * 1.0 / size * image.getHeight()),
                    image.getWidth(), image.getHeight() / size);
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            ImageOutputStream ios = ImageIO.createImageOutputStream(bs);
            ImageIO.write(subImage, "png", ios);
            printStream = new ByteArrayInputStream(bs.toByteArray());
        } catch (IOException ex) {
            throw new RuntimeException("inputStream convert fail");
        }

        DocFlavor dof = DocFlavor.INPUT_STREAM.PNG;
        HashPrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
        pras.add(OrientationRequested.PORTRAIT);
        pras.add(PrintQuality.HIGH);
        pras.add(new Copies(1));
        pras.add(new MediaPrintableArea(0, 0, width, height, MediaPrintableArea.MM));
        try {
            Doc doc = new SimpleDoc(printStream, dof, null);
            DocPrintJob job = printService.createPrintJob();
            job.addPrintJobListener(new PrintJobAdapter() {
                @Override
                public void printJobNoMoreEvents(PrintJobEvent pje) {
                }
            });
            job.print(doc, pras);
            listener.onPrint(uuidCode, Boolean.TRUE, getLabelInfoByUuidCode(uuidCode),
                    Constants.SUCCESS, "print success, please wait for next print job");
            return true;
        } catch (PrintException ex) {
            ex.printStackTrace();
            listener.onPrint(uuidCode, Boolean.FALSE, getLabelInfoByUuidCode(uuidCode),
                    Constants.PRINTER_NOT_EXIST, "print failed, please check if the printer available");
            return false;
        }
    }

    private LabelInfo getLabelInfoByUuidCode(String uuidCode) {
        LabelInfo labelInfo;
        YXMessage message = new YXMessage(UUID.randomUUID().toString(), 0,
                LABEL_UUID_TOPIC, SOURCE, TARGET, "", uuidCode);
        Response<LabelData> response;
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                JSONObject.toJSONString(message));
        try {
            response = getWaybillService().findPictureByPath(body).execute();
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException("server error");
        }
        if (response.isSuccessful()) {
            labelInfo = response.body().data;
        } else {
            throw new RuntimeException("labelNotExist");
        }

        return labelInfo;
    }

    private InputStream downloadImageStream(String imageUrl) {
        URL url;
        try {
            url = new URL(imageUrl);
            DataInputStream dataInputStream = new DataInputStream(url.openStream());

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int length;

            while ((length = dataInputStream.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
            dataInputStream.close();

            return new ByteArrayInputStream(output.toByteArray());
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Getter(lazy = true)
    private final WaybillService waybillService = buildWaybillService(serverAddress);

    private WaybillService buildWaybillService(String serverAddress) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SignatureInterceptor(accessKey, accessSecret))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .build();

        return new Retrofit.Builder()
                .baseUrl(serverAddress)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build()
                .create(WaybillService.class);
    }

    private ServerService buildBestHealthServer(String url) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .build();

        return new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build()
                .create(ServerService.class);
    }
}
