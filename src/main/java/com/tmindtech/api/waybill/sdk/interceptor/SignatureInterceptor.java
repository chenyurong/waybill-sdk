package com.tmindtech.api.waybill.sdk.interceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Random;
import javax.annotation.Nonnull;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * 这是一个拦截器, 他会处理所有即将发出去请求, 在这里可以给所有请求统一增加请求头
 */
public class SignatureInterceptor implements Interceptor {
    private static final String ALGORITHM = "SHA-1";
    private static char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private final String accessKey;
    private final String accessSecret;

    public SignatureInterceptor(@Nonnull String accessKey, @Nonnull String accessSecret) {
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        //URL 参数规范化
        Request request = chain.request();

        StringBuffer sb = new StringBuffer();

        if (request.method().equals("POST")) {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            sb.append(buffer.readString(StandardCharsets.UTF_8));
        }

        String body = sb.toString();

        int random = Math.abs(new Random().nextInt());
        long timestamp = Timestamp.from(Instant.now()).getTime();
        String sign = sha1(sha1(accessKey + accessSecret) + body + random + timestamp);
        return chain.proceed(chain.request().newBuilder()
                .header("appCode", accessKey)
                .header("random", String.valueOf(random))
                .header("timestamp", String.valueOf(timestamp))
                .header("signature", sign)
                .put(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
                .build()
        );
    }

    private String sha1(String origin) {
        if (origin == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(origin.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();
            int len = bytes.length;
            StringBuilder sb = new StringBuilder(len * 2);
            for (byte aByte : bytes) {
                sb.append(DIGITS_LOWER[(aByte >> 4) & 0x0f]);
                sb.append(DIGITS_LOWER[aByte & 0x0f]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
