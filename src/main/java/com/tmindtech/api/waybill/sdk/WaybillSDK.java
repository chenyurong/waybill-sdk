package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.interceptor.SignatureInterceptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 实际上的这个 SDK 功能相当复杂, 这个 demo 的结构可能是完全不对的
 * 这个 SDK 要判断当前仓库中, 各个本地服务是否可用, 如果一个本地服务不可用要自动切换使用另一个本地服务
 * 如果所有本地服务全部不可用则直接访问云端服务器(接口是一样的, 在内部实现切换)
 * 所有的一切都要在 SDK 内自动完成, 不需要外部干预
 */
public class WaybillSDK {
    private final String accessKey;
    private final String accessSecret;
    private PrintService printService;
    private PictureService pictureService = getPictureService();

    /**
     * SDK 初始化时传入必要的参数(由调用者传入)
     *
     * @param accessKey    accessKey
     * @param accessSecret accessSecret
     */
    public WaybillSDK(String accessKey, String accessSecret, String printerName) {
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;

        DocFlavor dof = DocFlavor.INPUT_STREAM.JPEG;
        HashAttributeSet hs = new HashAttributeSet();
        hs.add(new PrinterName(printerName, null));
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(dof, hs);
        if (printServices.length == 0) {
            System.out.println("printer initialize failure！");
            return;
        }
        System.out.println("printer initialize success！");
        System.out.println("here is all printers：");
        Arrays.asList(printServices).forEach(item -> System.out.println("printerName: " + item.getName()));
        printService = printServices[0];
    }

    /**
     * 获得所有可用打印机的名称
     *
     * @return 返回的所有可用打印机名称
     */
    public List<String> getAllPrintersName() {
        List<String> list = new ArrayList<>();
        DocFlavor dof = DocFlavor.INPUT_STREAM.JPEG;
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(dof, null);
        if (printServices.length != 0) {
            Arrays.asList(printServices).forEach(item -> list.add(item.getName()));
        }
        return list;
    }

    /**
     * 根据批次号打印面单
     *
     * @param batch          要打印面单的批次号
     * @param count          目标打印份数
     * @param printerXoffset 横向偏移量(毫米)
     * @param printerYoffset 纵向偏移量(毫米)
     * @param printerWidth   宽度(毫米)
     * @param printerHeight  高度(毫米)
     */
    public void printOrderByBatchNumber(String batch, int count, int printerXoffset,
                                        int printerYoffset, int printerWidth, int printerHeight) {
        WaybillService billService = getWaybillService();
        try {
            List<String> paths = billService.getOrderNamesByBatchNumber(batch).execute().body();
            if (paths != null && paths.size() != 0) {
                paths.forEach(path -> {
                    DocFlavor dof = DocFlavor.INPUT_STREAM.JPEG;
                    PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
                    pras.add(OrientationRequested.PORTRAIT);
                    pras.add(PrintQuality.HIGH);
                    pras.add(new Copies(count));
                    DocAttributeSet das = new HashDocAttributeSet();
                    das.add(new MediaPrintableArea(printerXoffset, printerYoffset,
                            printerWidth, printerHeight, MediaPrintableArea.MM));
                    try {
                        Doc doc = new SimpleDoc(pictureService.getOrderPictureByPath(path).execute().body().byteStream(), dof, das);
                        DocPrintJob job = printService.createPrintJob();
                        job.print(doc, pras);
                    } catch (PrintException ex) {
                        System.out.println("printer failure!");
                        ex.printStackTrace();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public WaybillService getWaybillService() {
        //每个 OkHttpClient 都会有一个线程池, 如果拦截器不会变化的话, 可以缓存下来, 每次都重新生成一个 OkHttpClient 可能有性能问题
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SignatureInterceptor(accessKey, accessSecret))
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))    //实际工作时不要用 BODY, 输出太多了
                .build();

        return new Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build()
                .create(WaybillService.class);
    }

    public PictureService getPictureService() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();

        return new Retrofit.Builder()
                .baseUrl(Constants.GET_PICTURE_URL)
                .client(okHttpClient)
                .build()
                .create(PictureService.class);
    }
}
