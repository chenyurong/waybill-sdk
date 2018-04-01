package com.tmindtech.api.waybill.sdk.interceptor;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import lombok.AllArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@AllArgsConstructor
public class HotSwitchInterceptor implements Interceptor {
    private final List<URL> localServerBaseUrls;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

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
                return chain.proceed(localRequest);
            } catch (IOException ignored) {

            }
        }

        return chain.proceed(request);
    }
}
