package shit.zen.gui;

import java.awt.Color;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.Paint;
import shit.zen.event.EventTarget;

public class IntroAnimation
extends ClientBase {
    private static volatile boolean isActive = false;
    private long startTime = -1L;
    private boolean finished = false;

    private static final String TITLE_TEXT = "ZENAMX";
    private static final String SUBTITLE_TEXT = "Base on OpenZen";

    public IntroAnimation() {
        isActive = true;
    }

    public static boolean isRunning() {
        return isActive;
    }

    @EventTarget(value=4)
    public void onRender(GlRenderEvent glRenderEvent) {
        float bgAlpha;
        long elapsed;
        if (this.finished) {
            return;
        }
        if (this.startTime < 0L) {
            this.startTime = System.currentTimeMillis();
        }
        elapsed = System.currentTimeMillis() - this.startTime;
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        // 时间轴：主标题(ZENAMX) 缩放淡入 -> 延迟一小段 -> 副标题(Base on OpenZen) 淡入上滑 -> 停留 -> 整体淡出
        long titleAppearStart = 1100L;
        long titleAppearDuration = 900L;
        long subtitleDelay = 400L;
        long subtitleAppearDuration = 500L;
        long holdDuration = 1500L;
        long fadeOutDuration = 700L;

        long subtitleAppearStart = titleAppearStart + titleAppearDuration + subtitleDelay;
        long fadeOutStart = subtitleAppearStart + subtitleAppearDuration + holdDuration;

        if (elapsed <= 800L) {
            float fadeIn = IntroAnimation.easeOutCubic(IntroAnimation.clamp01((float)elapsed / 800.0f));
            bgAlpha = 0.6f * fadeIn;
        } else if (elapsed <= fadeOutStart) {
            bgAlpha = 0.6f;
        } else if (elapsed <= fadeOutStart + fadeOutDuration) {
            float fadeOut = 1.0f - IntroAnimation.easeInCubic(IntroAnimation.clamp01((float)(elapsed - fadeOutStart) / (float) fadeOutDuration));
            bgAlpha = 0.6f * fadeOut;
        } else {
            this.finish();
            return;
        }

        Paint paint = GlHelper.toPaint(new Color(0, 0, 0, (int)(bgAlpha * 255.0f)));
        GlHelper.drawRect(0.0f, 0.0f, screenWidth, screenHeight, paint);

        // 整体淡出系数，最后 fadeOutDuration 内让标题和副标题一起消失
        float fadeFactor = 1.0f;
        if (elapsed > fadeOutStart) {
            fadeFactor = 1.0f - IntroAnimation.clamp01((float)(elapsed - fadeOutStart) / (float) fadeOutDuration);
        }

        // 主标题：ZENAMX，缩放 2.0 -> 1.0，同时淡入
        float titleScale = 2.0f;
        float titleAlpha = 0.0f;
        if (elapsed >= titleAppearStart) {
            long sinceTitle = elapsed - titleAppearStart;
            if (sinceTitle <= titleAppearDuration) {
                float titleProgress = IntroAnimation.easeOutCubic(IntroAnimation.clamp01((float)sinceTitle / (float) titleAppearDuration));
                titleScale = IntroAnimation.lerp(2.0f, 1.0f, titleProgress);
                titleAlpha = titleProgress;
            } else {
                titleScale = 1.0f;
                titleAlpha = 1.0f;
            }
        }

        FontRenderer titleFont = FontPresets.axiformaBold(64.0f * titleScale);
        float titleWidth = GlHelper.getStringWidth(TITLE_TEXT, titleFont);
        float titleRenderX = centerX - titleWidth / 2.0f;
        float titleRenderY = centerY - titleFont.getMetrics().capHeight() / 2.0f;
        int titleColor = new Color(1.0f, 1.0f, 1.0f, IntroAnimation.clamp01(titleAlpha * fadeFactor)).getRGB();
        GlHelper.drawText(TITLE_TEXT, titleRenderX, titleRenderY, titleFont, titleColor);

        // 副标题：Base on OpenZen，淡入 + 轻微上滑，位置在主标题正下方
        float subtitleAlpha = 0.0f;
        float subtitleOffsetY = 8.0f;
        if (elapsed > subtitleAppearStart && elapsed <= subtitleAppearStart + subtitleAppearDuration) {
            float subtitleProgress = IntroAnimation.easeOutCubic((float)(elapsed - subtitleAppearStart) / (float) subtitleAppearDuration);
            subtitleAlpha = subtitleProgress;
            subtitleOffsetY = (1.0f - subtitleProgress) * 8.0f;
        } else if (elapsed > subtitleAppearStart + subtitleAppearDuration) {
            subtitleAlpha = 1.0f;
            subtitleOffsetY = 0.0f;
        }

        if (subtitleAlpha > 0.0f) {
            FontRenderer subtitleFont = FontPresets.axiformaBold(16.0f);
            float subtitleWidth = GlHelper.getStringWidth(SUBTITLE_TEXT, subtitleFont);
            float subtitleRenderX = centerX - subtitleWidth / 2.0f;
            // 固定用 1.0 倍字号下的标题基准位置计算行距，避免标题缩放期间副标题跟着跳动
            FontRenderer baseTitleFont = FontPresets.axiformaBold(64.0f);
            float baseTitleY = centerY - baseTitleFont.getMetrics().capHeight() / 2.0f;
            float subtitleRenderY = baseTitleY + baseTitleFont.getMetrics().capHeight() + 10.0f + subtitleOffsetY;
            int subtitleColor = new Color(1.0f, 1.0f, 1.0f, IntroAnimation.clamp01(subtitleAlpha * fadeFactor * 0.75f)).getRGB();
            GlHelper.drawText(SUBTITLE_TEXT, subtitleRenderX, subtitleRenderY, subtitleFont, subtitleColor);
        }
    }

    private void finish() {
        if (!this.finished) {
            this.finished = true;
            try {
                ZenClient.instance.getEventBus().unregister(this);
            } catch (Throwable throwable) {
                // empty catch block
            }
            isActive = false;
        }
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float easeOutCubic(float t) {
        float clamped = IntroAnimation.clamp01(t);
        clamped = (float)(1.0 - Math.pow(1.0f - clamped, 3.0));
        return clamped;
    }

    private static float easeInCubic(float t) {
        float clamped = IntroAnimation.clamp01(t);
        clamped = clamped * clamped * clamped;
        return clamped;
    }
}
