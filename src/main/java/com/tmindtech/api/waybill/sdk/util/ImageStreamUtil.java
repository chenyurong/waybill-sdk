package com.tmindtech.api.waybill.sdk.util;

import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import javafx.print.PrintResolution;
import javafx.print.Printer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 图片流处理工具类
 */
public class ImageStreamUtil {
    // 1英寸是25.4毫米
    private static final float INCH_2_MM = 25.4f;

    /**
     * @param inputStream 需要转换的目标图片流
     * @return 转换后的结果图片流 如果打印机未设置成功，会抛出NullPointerException异常
     */
    public static InputStream convertImageStream2MatchPrinter(InputStream inputStream) {
        InputStreamCacher cacher = new InputStreamCacher(inputStream);
        Set<PrintResolution> set = Printer.getDefaultPrinter().getPrinterAttributes().getSupportedPrintResolutions();
        Optional<PrintResolution> optional = set.stream().max(Comparator.comparingInt(PrintResolution::getFeedResolution));
        PrintResolution resolution = optional.orElseGet(null);
        try {
            BufferedImage image = ImageIO.read(cacher.getInputStream());
            int width = image.getWidth();
            int height = image.getHeight();
            int crossResolution = 0;
            int feedResolution = 0;

            ImageInputStream iis = ImageIO.createImageInputStream(cacher.getInputStream());
            Iterator it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) {
                return null;
            }
            ImageReader reader = (ImageReader) it.next();
            reader.setInput(iis);

            IIOMetadata meta = reader.getImageMetadata(0);
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_1.0");
            NodeList nodes = root.getElementsByTagName("HorizontalPixelSize");
            if (nodes.getLength() > 0) {
                IIOMetadataNode dpcWidth = (IIOMetadataNode) nodes.item(0);
                NamedNodeMap nnm = dpcWidth.getAttributes();
                Node item = nnm.item(0);
                crossResolution = Math.round(INCH_2_MM / Float.parseFloat(item.getNodeValue()));
            }
            if (nodes.getLength() > 0) {
                nodes = root.getElementsByTagName("VerticalPixelSize");
                IIOMetadataNode dpcHeight = (IIOMetadataNode) nodes.item(0);
                NamedNodeMap nnm = dpcHeight.getAttributes();
                Node item = nnm.item(0);
                feedResolution = Math.round(INCH_2_MM / Float.parseFloat(item.getNodeValue()));
            }

            /*
            // 可以用ImageInfo直接拿图片的宽和高以及DPI，但是需要引入sanselan包
            // 另外ImageInfo支持的图片类型有限，所有用上面的方法获取DPI
            ImageInfo imageInfo = Sanselan.getImageInfo(cacher.getInputStream(), null);
            int width = imageInfo.getWidth();
            int height = imageInfo.getHeight();
            int crossResolution = imageInfo.getPhysicalWidthDpi();
            int feedResolution = imageInfo.getPhysicalHeightDpi();
             */

            int convertWidth = Math.round((float) width * resolution.getCrossFeedResolution() / crossResolution);
            int convertHeight = Math.round((float) height * resolution.getFeedResolution() / feedResolution);

            BufferedImage bufferedImage = fastResample(ImageIO.read(cacher.getInputStream()), null, convertWidth, convertHeight, 1);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", bos);
            return ImageStreamUtil.setImageDpi(new ByteArrayInputStream(bos.toByteArray()), convertWidth, convertHeight, crossResolution, feedResolution);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * 设置图片的DPI
     *
     * @param inputStream 图片输入流
     * @param width       width
     * @param height      height
     * @param crossDpi    水平DPI
     * @param feedDpi     垂直DPI
     * @return 返回重新设置DPI后的图片流
     */
    private static InputStream setImageDpi(InputStream inputStream, int width, int height, int crossDpi, int feedDpi) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
            reader.setInput(new MemoryCacheImageInputStream(inputStream), true, false);
            BufferedImage image = reader.read(0);

            Image rescaled = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
            BufferedImage output = toBufferedImage(rescaled, BufferedImage.TYPE_INT_RGB);

            JPEGImageEncoder jpegEncoder = JPEGCodec.createJPEGEncoder(outputStream);
            JPEGEncodeParam jpegEncodeParam = jpegEncoder.getDefaultJPEGEncodeParam(output);
            jpegEncodeParam.setDensityUnit(JPEGEncodeParam.DENSITY_UNIT_DOTS_INCH);
            jpegEncodeParam.setQuality(1.0f, true);
            jpegEncodeParam.setXDensity(crossDpi);
            jpegEncodeParam.setYDensity(feedDpi);

            jpegEncoder.encode(output, jpegEncodeParam);
            outputStream.flush();
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static BufferedImage toBufferedImage(Image image, int type) {
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        BufferedImage result = new BufferedImage(w, h, type);
        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }

    /**
     * 最小相邻采样算法缩放图片
     *
     * @param input  input image
     * @param output output image
     * @param width  width
     * @param height height
     * @param type   type
     * @return 缩放后的图片缓冲流
     */
    private static BufferedImage fastResample(final BufferedImage input, final BufferedImage output, final int width, final int height, final int type) {
        BufferedImage temp = input;

        double xScale;
        double yScale;

        AffineTransform transform;
        AffineTransformOp scale;

        if (type > AffineTransformOp.TYPE_NEAREST_NEIGHBOR) {
            // Initially scale so all remaining operations will halve the image
            if (width < input.getWidth() || height < input.getHeight()) {
                int w = width;
                int h = height;
                while (w < input.getWidth() / 2) {
                    w *= 2;
                }
                while (h < input.getHeight() / 2) {
                    h *= 2;
                }

                xScale = w / (double) input.getWidth();
                yScale = h / (double) input.getHeight();

                //System.out.println("First scale by x=" + xScale + ", y=" + yScale);

                transform = AffineTransform.getScaleInstance(xScale, yScale);
                scale = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
                temp = scale.filter(temp, null);
            }
        }

        scale = null; // NOTE: This resets!

        xScale = width / (double) temp.getWidth();
        yScale = height / (double) temp.getHeight();

        if (type > AffineTransformOp.TYPE_NEAREST_NEIGHBOR) {
            // TODO: Test skipping first scale (above), and instead scale once
            // more here, and a little less than .5 each time...
            // That would probably make the scaling smoother...
            while (xScale < 0.5 || yScale < 0.5) {
                if (xScale >= 0.5) {
                    transform = AffineTransform.getScaleInstance(1.0, 0.5);
                    scale = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);

                    yScale *= 2.0;
                } else if (yScale >= 0.5) {
                    transform = AffineTransform.getScaleInstance(0.5, 1.0);
                    scale = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);

                    xScale *= 2.0;
                } else {
                    xScale *= 2.0;
                    yScale *= 2.0;
                }

                if (scale == null) {
                    transform = AffineTransform.getScaleInstance(0.5, 0.5);
                    scale = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
                }

                temp = scale.filter(temp, null);
            }
        }
        transform = AffineTransform.getScaleInstance(xScale, yScale);
        scale = new AffineTransformOp(transform, type);

        return scale.filter(temp, output);
    }

    public static void main(String[] args) {
    }
}
