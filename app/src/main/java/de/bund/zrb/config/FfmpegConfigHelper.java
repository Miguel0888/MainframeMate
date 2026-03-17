package de.bund.zrb.config;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.util.Locale;
import java.util.Map;

/**
 * FFmpeg-specific configuration helper. Separated from {@link VideoConfig} so that
 * VideoConfig can be loaded without requiring FFmpeg/JavaCV on the classpath.
 * Only instantiate / call methods in this class when FFmpeg is known to be available.
 */
public final class FfmpegConfigHelper {

    private FfmpegConfigHelper() {}

    /** Maps codec name string to FFmpeg codec ID. */
    public static int mapCodecId(String codecName) {
        String c = codecName == null ? "" : codecName.toLowerCase(Locale.ROOT);
        switch (c) {
            case "mjpeg":   return avcodec.AV_CODEC_ID_MJPEG;
            case "libx264":
            case "h264":    return avcodec.AV_CODEC_ID_H264;
            case "libx265":
            case "hevc":    return avcodec.AV_CODEC_ID_HEVC;
            default:        return avcodec.AV_CODEC_ID_NONE;
        }
    }

    /** Maps pixel format name to FFmpeg pixel format constant. */
    public static int mapPixelFmt(String pf) {
        if (pf == null) return avutil.AV_PIX_FMT_YUV420P;
        switch (pf.toLowerCase(Locale.ROOT)) {
            case "yuv420p":  return avutil.AV_PIX_FMT_YUV420P;
            case "yuvj420p": return avutil.AV_PIX_FMT_YUVJ420P;
            case "yuv422p":  return avutil.AV_PIX_FMT_YUV422P;
            case "yuv444p":  return avutil.AV_PIX_FMT_YUV444P;
            case "bgr24":    return avutil.AV_PIX_FMT_BGR24;
            case "rgb24":    return avutil.AV_PIX_FMT_RGB24;
            default:         return avutil.AV_PIX_FMT_YUV420P;
        }
    }

    /**
     * Applies all video options from {@link VideoConfig} to an FFmpegFrameRecorder.
     * @param recObj the FFmpegFrameRecorder instance (passed as Object for decoupling)
     * @param fps frames per second
     */
    public static void configureRecorder(Object recObj, int fps) {
        FFmpegFrameRecorder rec = (FFmpegFrameRecorder) recObj;
        rec.setFrameRate(fps);
        rec.setInterleaved(VideoConfig.isInterleaved());

        // Codec
        int codecId = mapCodecId(VideoConfig.getCodec());
        if (codecId != avcodec.AV_CODEC_ID_NONE) {
            rec.setVideoCodec(codecId);
        } else {
            rec.setVideoCodecName(VideoConfig.getCodec());
        }

        // PixFmt
        rec.setPixelFormat(mapPixelFmt(VideoConfig.getPixelFmt()));

        // Quality
        switch (VideoConfig.getQualityMode()) {
            case "qscale":
                rec.setVideoOption("qscale", String.valueOf(VideoConfig.getQscale()));
                break;
            case "crf":
                rec.setVideoOption("crf", String.valueOf(VideoConfig.getCrf()));
                break;
            case "bitrate":
                if (VideoConfig.getBitrateKbps() > 0) rec.setVideoBitrate(VideoConfig.getBitrateKbps() * 1000);
                break;
        }

        // Color range/space
        setOpt(rec, "color_range", VideoConfig.getColorRange());
        setOpt(rec, "colorspace", VideoConfig.getColorspace());
        setOpt(rec, "color_trc", VideoConfig.getColorTrc());
        setOpt(rec, "color_primaries", VideoConfig.getColorPrimaries());

        // Filter
        setOpt(rec, "vf", VideoConfig.getVf());

        // Threads
        if (VideoConfig.getThreads() > 0) rec.setVideoOption("threads", String.valueOf(VideoConfig.getThreads()));

        // x264/x265 extras
        setOpt(rec, "preset", VideoConfig.getPreset());
        setOpt(rec, "tune", VideoConfig.getTune());
        setOpt(rec, "profile", VideoConfig.getProfile());
        setOpt(rec, "level", VideoConfig.getLevel());

        // Arbitrary extra options
        for (Map.Entry<String, String> e : VideoConfig.getExtraVideoOptions().entrySet()) {
            if (e.getKey() != null && !e.getKey().trim().isEmpty() && e.getValue() != null) {
                rec.setVideoOption(e.getKey(), e.getValue());
            }
        }
    }

    private static void setOpt(FFmpegFrameRecorder rec, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            rec.setVideoOption(key, value);
        }
    }
}

