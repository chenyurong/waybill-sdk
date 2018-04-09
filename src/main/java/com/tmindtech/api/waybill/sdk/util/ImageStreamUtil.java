package com.tmindtech.api.waybill.sdk.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.print.PrintService;
import javax.print.attribute.standard.PrinterResolution;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 图片流处理工具类
 */
@SuppressWarnings("sunapi")
public class ImageStreamUtil {
    // 1英寸是25.4毫米
    private static final float INCH_2_MM = 25.4f;

    /**
     * @param inputStream 需要转换的目标图片流
     * @return 转换后的结果图片流 如果打印机未设置成功，会抛出NullPointerException异常
     */
    public static InputStream convertImageStream2MatchPrinter(InputStream inputStream, PrintService printService) {
        InputStreamCacher cacher = new InputStreamCacher(inputStream);
        PrinterResolution resolution;
        PrinterResolution[] resolutions = (PrinterResolution[]) printService.getSupportedAttributeValues(PrinterResolution.class, null, null);
        if (Objects.isNull(resolutions) || resolutions.length == 0) {
            resolution = new PrinterResolution(300, 300, PrinterResolution.DPI);
        } else {
            resolution = resolutions[0];
        }

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
            
            int xdpi = resolution.getCrossFeedResolution(PrinterResolution.DPI);
            int ydpi = resolution.getFeedResolution(PrinterResolution.DPI);
            int convertWidth = Math.round((float) width * xdpi / crossResolution);
            int convertHeight = Math.round((float) height * ydpi / feedResolution);

            BufferedImage bufferedImage = fastResample(ImageIO.read(cacher.getInputStream()), null, convertWidth, convertHeight, 1);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BufferedOutputStream bufferStream = new BufferedOutputStream(bos);
            ImageIO.write(bufferedImage, "png", bufferStream);
            bufferStream.close();
            return resetImageDpi(new ByteArrayInputStream(bos.toByteArray()), convertWidth, convertHeight, xdpi, ydpi);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }


    /**
     * 重置图片的dpi
     *
     * @param inputStream 图片输入流
     * @param crossDpi    水平dpi
     * @param feedDpi     垂直dpi
     * @return 重置后的图片流
     */
    private static InputStream resetImageDpi(InputStream inputStream, int width, int height, int crossDpi, int feedDpi) {
        BufferedImage image = scaleImage(width, height, inputStream);
        for (Iterator<ImageWriter> iw = ImageIO.getImageWritersByFormatName("png"); iw.hasNext(); ) {
            ImageWriter writer = iw.next();
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
            IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);
            if (metadata.isReadOnly() || !metadata.isStandardMetadataFormatSupported()) {
                continue;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageOutputStream stream = null;
            try {
                setDPI(metadata, crossDpi, feedDpi);
                stream = ImageIO.createImageOutputStream(output);
                writer.setOutput(stream);
                writer.write(metadata, new IIOImage(image, null, metadata), writeParam);
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException ignore) {
                }
            }
            return new ByteArrayInputStream(output.toByteArray());
        }
        return null;
    }

    private static BufferedImage scaleImage(int width, int height, InputStream inputStream) {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.addRenderingHints(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY));
        try {
            g2d.drawImage(ImageIO.read(inputStream), 0, 0, width, height, null);
        } catch (IOException ignore) {
        }
        return bi;
    }

    private static void setDPI(IIOMetadata metadata, int crossDpi, int feedDpi) {

        // for PMG, it's dots per millimeter
        double crossDotsPerMilli = 1.0 * crossDpi / INCH_2_MM;
        double feedDotsPerMilli = 1.0 * feedDpi / INCH_2_MM;
        IIOMetadataNode horiz = new IIOMetadataNode("HorizontalPixelSize");
        horiz.setAttribute("value", Double.toString(crossDotsPerMilli));

        IIOMetadataNode vert = new IIOMetadataNode("VerticalPixelSize");
        vert.setAttribute("value", Double.toString(feedDotsPerMilli));

        IIOMetadataNode dim = new IIOMetadataNode("Dimension");
        dim.appendChild(horiz);
        dim.appendChild(vert);

        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_1.0");
        root.appendChild(dim);

        try {
            metadata.mergeTree("javax_imageio_1.0", root);
        } catch (IIOInvalidTreeException ex) {
            ex.printStackTrace();
        }
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
