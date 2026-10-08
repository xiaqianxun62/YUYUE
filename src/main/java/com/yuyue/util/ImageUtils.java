package com.yuyue.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

/**
 * 图片处理工具：压缩、缩放、格式统一。
 * 纯 JDK 实现（javax.imageio），零第三方依赖。
 *
 * 设计约束：
 *   1. GIF 动图跳过压缩（ImageIO.write 不能保留动画帧），直接 copy 原图
 *   2. PNG/WEBP 等统一转 JPEG（体积更小，透明背景用白底填充）
 *   3. 目标尺寸内按比例缩放（保持宽高比）
 */
@Slf4j
public final class ImageUtils {

    /** 头像缩略图尺寸（正方形，宽高上限） */
    public static final int AVATAR_THUMB_SIZE = 200;
    /** 封面缩略图尺寸（正方形，宽高上限） */
    public static final int COVER_THUMB_SIZE = 800;
    /** JPEG 压缩质量（0-1） */
    public static final float JPEG_QUALITY = 0.80f;

    private ImageUtils() {}

    /**
     * 判断是否 GIF 动图（content-type 或文件扩展名）
     */
    public static boolean isGif(String contentType, String fileName) {
        if (contentType != null && contentType.toLowerCase().contains("gif")) return true;
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && fileName.substring(dot + 1).equalsIgnoreCase("gif")) return true;
        }
        return false;
    }

    /**
     * 压缩并生成缩略图。
     *
     * @param src           源文件路径
     * @param thumb         缩略图输出路径（一律 .jpg）
     * @param maxWidth      最大宽度
     * @param maxHeight     最大高度
     * @param contentType   源文件 content-type（用于判断 GIF）
     * @param originalName  原始文件名（用于判断 GIF）
     * @return 成功写入缩略图 → true；GIF 跳过压缩 → false
     * @throws IOException 读写异常
     */
    public static boolean generateThumbnail(Path src, Path thumb, int maxWidth, int maxHeight,
                                            String contentType, String originalName) throws IOException {
        // GIF 跳过压缩，直接 copy
        if (isGif(contentType, originalName)) {
            Files.copy(src, thumb, StandardCopyOption.REPLACE_EXISTING);
            return false;
        }

        BufferedImage original;
        try (var in = Files.newInputStream(src)) {
            original = ImageIO.read(in);
        }
        if (original == null) {
            // ImageIO 读不出来（罕见格式），降级 copy
            log.warn("ImageIO 无法解析，降级 copy: {}", src.getFileName());
            Files.copy(src, thumb, StandardCopyOption.REPLACE_EXISTING);
            return false;
        }

        int srcW = original.getWidth();
        int srcH = original.getHeight();

        // 计算目标尺寸（保持宽高比，不超过 max）
        int destW = srcW;
        int destH = srcH;
        if (srcW > maxWidth || srcH > maxHeight) {
            double ratio = Math.min((double) maxWidth / srcW, (double) maxHeight / srcH);
            destW = (int) Math.round(srcW * ratio);
            destH = (int) Math.round(srcH * ratio);
        }

        // 新画布（TYPE_INT_RGB 确保 JPEG 无透明通道，白底填充）
        BufferedImage scaled = new BufferedImage(destW, destH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            // 白底（防止原图透明 PNG 转 JPEG 出现黑底）
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, destW, destH);
            // 高质量缩放采样
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, destW, destH, null);
        } finally {
            g.dispose();
        }

        // 写入 JPEG，带质量控制
        writeJpeg(scaled, thumb, JPEG_QUALITY);
        return true;
    }

    /**
     * 用指定质量写 JPEG。ImageIO.write(jpeg) 不直接支持 quality，
     * 需要拿到 JPEGImageWriteParam 手动 setCompressionQuality。
     */
    private static void writeJpeg(BufferedImage image, Path out, float quality) throws IOException {
        Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            // 兜底：用默认 ImageIO.write
            ImageIO.write(image, "jpg", out.toFile());
            return;
        }
        javax.imageio.ImageWriter writer = writers.next();
        try (var outStream = Files.newOutputStream(out)) {
            javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(outStream);
            writer.setOutput(ios);
            javax.imageio.plugins.jpeg.JPEGImageWriteParam param =
                    (javax.imageio.plugins.jpeg.JPEGImageWriteParam) writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            ios.flush();
        } finally {
            writer.dispose();
        }
    }

    /**
     * 根据原图路径推导出缩略图路径：把最后一个扩展名替换成 _thumb.jpg
     * 例：/uploads/avatar_2_1789979220803_3.jpeg → /uploads/avatar_2_1789979220803_3_thumb.jpg
     */
    public static String thumbFileName(String originalFileName) {
        if (originalFileName == null) return null;
        int lastDot = originalFileName.lastIndexOf('.');
        if (lastDot < 0) return originalFileName + "_thumb.jpg";
        return originalFileName.substring(0, lastDot) + "_thumb.jpg";
    }
}
