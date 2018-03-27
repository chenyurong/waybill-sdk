package com.tmindtech.api.waybill.sdk;

import com.sun.org.apache.xpath.internal.operations.Bool;
import com.tmindtech.api.waybill.sdk.exception.AwesomeException;
import com.tmindtech.api.waybill.sdk.interceptor.HotSwitchInterceptor;
import com.tmindtech.api.waybill.sdk.interceptor.SignatureInterceptor;
import com.tmindtech.api.waybill.sdk.model.LogicUriInfo;
import com.tmindtech.api.waybill.sdk.model.PrintResult;
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
import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.HashAttributeSet;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.MediaPrintableArea;
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
 * 实际上的这个 SDK 功能相当复杂, 这个 demo 的结构可能是完全不对的
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
     * 设置当前打印机
     *
     * @param name 要设置的指定打印机
     * @return true设置成功，false设置失败
     */
    public boolean setCurrentPrinter(String name) {
        DocFlavor dof = DocFlavor.INPUT_STREAM.JPEG;
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
     * 设置打印结果监听, 接收打印结果异步回调
     *
     * @param listener 打印结果监听器
     */
    public void setPrintListener(PrintListener listener) {
        this.listener = listener;
    }

    /**
     * 通过批次号获取面单逻辑地址列表（兼容一件多单）
     *
     * @param saleOrder 出库批次号
     * @return 面单逻辑地址列表
     */
    public List<LogicUriInfo> getWaybillAddress(String saleOrder) {
        List<LogicUriInfo> uriInfos = new ArrayList<>();
        List<String> uris;
        try {
            uris = getWaybillService().getOrderNamesByBatchNumber(saleOrder).execute().body();
        } catch (IOException ex) {
            throw new AwesomeException(Config.SALE_ORDER_ERROR_OR_NOT_EXIST);
        }
        if (uris != null) {
            uris.forEach(uri -> {
                LogicUriInfo info = new LogicUriInfo();
                info.logicUri = uri;
                try {
                    info.isReady = getPictureService().getOrderPictureByPath(uri).execute().isSuccessful() ? Boolean.TRUE : Boolean.FALSE;
                    uriInfos.add(info);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    info.isReady = Boolean.FALSE;
                    uriInfos.add(info);
                }
            });
        }
        return uriInfos;
    }

    /**
     * 通过逻辑链接获取面单图片
     *
     * @param logicUri 面单逻辑地址
     * @return 面单图片流
     */
    public InputStream getWaybillImage(String logicUri) {
        try {
            Response<ResponseBody> response = getPictureService().getOrderPictureByPath(logicUri).execute();
            if (response != null && response.isSuccessful()) {
                return response.body().byteStream();
            } else {
                System.out.println("response的code" + response.code());
                throw new AwesomeException(Config.WAY_BILL_NOT_READY);
            }
        } catch (IOException ex) {
            throw new AwesomeException(Config.LOGIC_URI_ERROR_OR_NOT_EXIST);
        }
    }

    /**
     * 通过逻辑链接请求打印面单. 支持批量打印, 并自动等待未生成的面单
     *
     * @param logicUriList   逻辑连接列表
     * @param count          份数
     * @param printerXoffset X偏移量
     * @param printerYoffset Y偏移量
     * @param printerWidth   宽度
     * @param printerHeight  高度
     * @return 打印结果以异步回调的方式进行通知
     */
    public void printWaybillByLogicUri(List<String> logicUriList, int count, int printerXoffset,
                                       int printerYoffset, int printerWidth, int printerHeight) {
        Thread thread = new Thread(() -> logicUriList.forEach(logicUrl -> {
            try {
                Response<ResponseBody> response = getPictureService().getOrderPictureByPath(logicUrl).execute();
                if (response.isSuccessful()) {
                    PrintResult printResult = printWaybill(response.body().byteStream(), count, printerXoffset, printerYoffset, printerWidth, printerHeight);
                    listener.onLogicUriPrint(logicUrl, printResult.isSuccess, printResult.code, printResult.result);
                } else {
                    listener.onLogicUriPrint(logicUrl, false, 0, "picture not exists");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                listener.onLogicUriPrint(logicUrl, false, -1, "server unreachable");
            }
        }));
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

    }

    /**
     * 通过批次号请求打印面单. 支持批量打印. 打印结果以异步回调方式进行通知
     *
     * @param saleOrderList  批次号列表
     * @param count          份数
     * @param printerXoffset X偏移量
     * @param printerYoffset Y偏移量
     * @param printerWidth   宽度
     * @param printerHeight  高度
     */
    public void printWaybillBySaleOrder(List<String> saleOrderList, int count, int printerXoffset,
                                        int printerYoffset, int printerWidth, int printerHeight) {
        Thread thread = new Thread(() ->
        {
            for (int i = 0; i < saleOrderList.size(); i++) {
                String saleOrder = saleOrderList.get(i);
                try {
                    Response<List<String>> response = getWaybillService().getOrderNamesByBatchNumber(saleOrder).execute();
                    if (response.isSuccessful()) {  //200
                        List<String> logicUris = response.body();
                        if (logicUris != null && logicUris.size() != 0) {
                            printWaybillByStream(logicUris, saleOrder, i, count, printerXoffset, printerYoffset, printerWidth, printerHeight);
                        }
                    } else {
                        if (response.code() == 404) {  //404
                            listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, -1, i, null, -3, "resource not exists");
                        } else if (response.code() == 102) {  //102
                            ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
                            final CountDownLatch countDownLatch = new CountDownLatch(1);
                            final Map<String, Future> futures = new HashMap<>();
                            final AtomicBoolean flag = new AtomicBoolean(Boolean.FALSE);
                            int finalI = i;
                            Future future = executor.scheduleWithFixedDelay(() -> {
                                if (flag.get() && futures.get("sale_order") != null) {
                                    futures.get("sale_order").cancel(true);
                                    countDownLatch.countDown();
                                }
                                try {
                                    Response<List<String>> newResponse = getWaybillService().getOrderNamesByBatchNumber(saleOrder).execute();
                                    if (newResponse.isSuccessful()) {
                                        List<String> logicUris = newResponse.body();
                                        if (logicUris != null && logicUris.size() != 0) {
                                            printWaybillByStream(logicUris, saleOrder, finalI, count, printerXoffset, printerYoffset, printerWidth, printerHeight);
                                        } else {
                                            listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, -1, finalI, null, -3, "resource not exists");
                                        }
                                        flag.set(Boolean.TRUE);
                                    }
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, -1, finalI, null, -1, "server unreachable");
                                    flag.set(Boolean.TRUE);
                                }
                            }, 0, 2, TimeUnit.SECONDS);
                            futures.put("sale_order", future);
                            try {
                                countDownLatch.await();
                            } catch (InterruptedException ex) {
                                log.info("countDownLatch await failure");
                                throw new AwesomeException(Config.ERROR_EXECUTOR_INTERRUPT);
                            }
                            executor.shutdown();
                        } else {  //500
                            listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, -1, i, null, -1, "server unreachable");
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, -1, i, null, -1, "server unreachable");
                }
            }
        }
        );
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private void printWaybillByStream(List<String> logicUriList, String saleOrder, int index, int count, int printerXoffset,
                                      int printerYoffset, int printerWidth, int printerHeight) {
        for (String logicUri : logicUriList) {
            try {
                Response<ResponseBody> pictureResponse = getPictureService().getOrderPictureByPath(logicUri).execute();
                if (pictureResponse.isSuccessful()) {
                    PrintResult result = printWaybill(pictureResponse.body().byteStream(), count, printerXoffset, printerYoffset, printerWidth, printerHeight);
                    listener.onSaleOrderPrint(saleOrder, result.isSuccess, logicUriList.size(), index, logicUri, result.code, result.result);
                } else {
                    listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, logicUriList.size(), index, logicUri, -3, "resource not exists");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                listener.onSaleOrderPrint(saleOrder, Boolean.FALSE, logicUriList.size(), index, logicUri, -1, "server unreachable");
            }
        }
    }

    private PrintResult printWaybill(InputStream inputStream, int count, int printerXoffset,
                                     int printerYoffset, int printerWidth, int printerHeight) {
        PrintResult result = new PrintResult();
        DocFlavor dof = DocFlavor.INPUT_STREAM.JPEG;
        PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
        pras.add(OrientationRequested.PORTRAIT);
        pras.add(PrintQuality.HIGH);
        pras.add(new Copies(count));
        DocAttributeSet das = new HashDocAttributeSet();
        das.add(new MediaPrintableArea(printerXoffset, printerYoffset,
                printerWidth, printerHeight, MediaPrintableArea.MM));
        try {
            Doc doc = new SimpleDoc(inputStream, dof, das);
            DocPrintJob job = printService.createPrintJob();
            job.addPrintJobListener(new PrintJobAdapter() {
                @Override
                public void printJobNoMoreEvents(PrintJobEvent pje) {
                    result.isSuccess = Boolean.TRUE;
                    result.code = pje.getPrintEventType();
                    result.result = "打印成功，等待其他打印服务";
                }
            });
            job.print(doc, pras);
        } catch (PrintException ex) {
            ex.printStackTrace();
            result.code = -2;
            result.isSuccess = Boolean.FALSE;
            result.result = "打印故障，请检查是否有可用打印设备";
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
