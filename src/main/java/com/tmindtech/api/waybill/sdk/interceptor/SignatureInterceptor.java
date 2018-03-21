package com.tmindtech.api.waybill.sdk.interceptor;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import javax.annotation.Nonnull;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.stream.Collectors;

import static com.tmindtech.api.waybill.sdk.Constants.DATE_TIME_FORMATTER;

/**
 * 这是一个拦截器, 他会处理所有即将发出去请求, 在这里可以给所有请求统一增加请求头
 */
public class SignatureInterceptor implements Interceptor {
    private static final String ALGORITHM = "HmacSHA256";

    private final String accessKey;
    private final String accessSecret;

    public SignatureInterceptor(@Nonnull String accessKey, @Nonnull String accessSecret) {
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        //得到 timestamp
        //不能改动全局时区, 因为这只是一个 SDK
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);

        //URL 参数规范化
        Request request = chain.request();
        HttpUrl httpUrl = request.url();
        String sortedQueryString = httpUrl.queryParameterNames().stream()
                .flatMap(name ->    //同一个名字的参数可能有多个
                        httpUrl.queryParameterValues(name).stream()
                                .map(value -> String.format("%s=%s", name, value))
                )
                .sorted()
                .collect(Collectors.joining("&"));
        String encodedQueryString = URLEncoder.encode(sortedQueryString, StandardCharsets.UTF_8.toString());

        //构造用于计算签名的字符串
        String stringToSign = String.format("%s&%s&%s&%s",
                request.method(),
                URLEncoder.encode("/", StandardCharsets.UTF_8.toString()),
                URLEncoder.encode(timestamp, StandardCharsets.UTF_8.toString()),
                encodedQueryString
        );

        //sha256 加密
        String sign;
        try {
            SecretKeySpec signingKey = new SecretKeySpec(accessSecret.getBytes(), ALGORITHM);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(signingKey);
            sign = new String(
                    Base64.getEncoder().encode(
                            mac.doFinal(stringToSign.getBytes())
                    )
            );
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e); //没有这个算法一定是 JDK 有问题, 无法处理
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid accessSecret");
        }

        return chain.proceed(
                request.newBuilder()
                        .headers(request.headers().newBuilder()
                                .add("X-Algorithm", ALGORITHM)
                                .add("X-Timestamp", timestamp)
                                .add("X-AccessKey", accessKey)
                                .add("X-Signature", sign)
                                .build()
                        )
                        .build()
        );
    }
}
