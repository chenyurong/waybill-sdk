package com.tmindtech.api.waybill.sdk.interceptor;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.AllArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;

@AllArgsConstructor
public class HotSwitchInterceptor implements Interceptor {
    public List<URL> localServerBaseUrls;
    public String serverAddress;

    @Override
    public Response intercept(Chain chain) throws IOException {
        long responseTime = -1;
        Request request = chain.request();
        Response response = null;

        for (URL localServerUrl : localServerBaseUrls) {
            Request localRequest = request.newBuilder()
                    .url(request.url().newBuilder()
                            .scheme(localServerUrl.getProtocol())
                            .host(localServerUrl.getHost())
                            .port(localServerUrl.getPort())
                            .build()
                    )
                    .build();
            try {
                long startTime = System.currentTimeMillis();
                Response responseSelect = chain.proceed(localRequest);
                if (responseSelect.isSuccessful()) {
                    long endTime = System.currentTimeMillis();
                    String content = IOUtils.toString(responseSelect.body().byteStream(), StandardCharsets.UTF_8);
                    if ("ok".equals(content)) {
                        if (responseTime == -1) {
                            responseTime = endTime - startTime;
                            serverAddress = localServerUrl.toString();
                            response = responseSelect;
                        } else {
                            if ((endTime - startTime) < responseTime) {
                                serverAddress = localServerUrl.toString();
                                response = responseSelect;
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return response;
    }
}
