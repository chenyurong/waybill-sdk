package com.tmindtech.api.waybill.sdk.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 缓存输入流(此类可以重用InputStream)
 */
public class InputStreamCacher {

    /**
     * 将InputStream中的字节保存到ByteArrayOutputStream中。
     */
    private ByteArrayOutputStream byteArrayOutputStream = null;

    public InputStreamCacher(InputStream inputStream) {
        if (Objects.isNull(inputStream)) {
            return;
        }
        byteArrayOutputStream = new ByteArrayOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(byteArrayOutputStream);
        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buffer)) > -1) {
                bos.write(buffer, 0, len);
            }
            bos.flush();
        } catch (IOException ignored) {
        }
        try {
            bos.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public InputStream getInputStream() {
        if (Objects.isNull(byteArrayOutputStream)) {
            return null;
        }
        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }
}
