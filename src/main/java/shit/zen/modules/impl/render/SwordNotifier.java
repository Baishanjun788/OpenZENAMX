package shit.zen.modules.impl.render;

import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.render.FontStore;
import shit.zen.settings.impl.BooleanSetting;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwordNotifier extends Module {

    public static SwordNotifier INSTANCE;

    public final BooleanSetting chatAlert = new BooleanSetting("Chat Alert", true);
    public final BooleanSetting redEsp = new BooleanSetting("Red ESP", true);
    public final BooleanSetting distanceText = new BooleanSetting("Distance Text", true);
    public final BooleanSetting sendToPublicChat = new BooleanSetting("Send To Public Chat", false);
    public final BooleanSetting screenFlash = new BooleanSetting("Screen Flash Alert", true);

    private final Set<String> markedPlayerNames = new HashSet<>();
    private final Set<String> alertedPlayerNames = new HashSet<>();

    private int textIndex = 0;
    private String pendingMessage = "";
    private long retryTime = 0;

    public SwordNotifier() {
        super("SwordNotifier", Category.RENDER);
        INSTANCE = this;
    }

    public boolean isMarked(String playerName) {
        if (!this.isEnabled() || playerName == null) return false;
        return this.markedPlayerNames.contains(playerName.toLowerCase().trim());
    }

    @Override
    public void onEnable() {
        this.clearMarkers();
        this.pendingMessage = "";
        this.retryTime = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.clearMarkers();
        super.onDisable();
    }

    private void clearMarkers() {
        this.markedPlayerNames.clear();
        this.alertedPlayerNames.clear();
    }

    /**
     * 🛠️ 彻底放弃外部工具类，调用原版最底层的玩家聊天发包
     */
    private void sendNativeChatMessage(String text) {
        if (mc.player == null || text.isEmpty()) return;
        // 原版自带的本地过滤与上报抽象，安全绕过混淆直接发送真实聊天包
        mc.player.connection.sendChat(text);
    }

    private void sendNextMessage(String name) {
        if (!this.sendToPublicChat.getValue()) return;

        String msg = "";
        switch (this.textIndex) {
            case 0 -> msg = "我看到了，" + name + "是杀手。";
            case 1 -> msg = name + "是杀手。";
            case 2 -> msg = "这个" + name + "是杀手!";
        }

        this.textIndex = (this.textIndex + 1) % 3;
        this.pendingMessage = msg;
        this.sendNativeChatMessage(msg);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.level == null || event.getPacket() == null) return;

        // 1. 底层拦截拔剑动作
        if (event.getPacket() instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            Entity entity = mc.level.getEntity(equipmentPacket.getEntity());
            if (entity instanceof Player targetPlayer && targetPlayer != mc.player) {

                for (Pair<EquipmentSlot, ItemStack> slotChange : equipmentPacket.getSlots()) {
                    EquipmentSlot slot = slotChange.getFirst();
                    ItemStack itemStack = slotChange.getSecond();

                    if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                            && itemStack.is(Items.DIAMOND_SWORD)) {

                        String rawName = targetPlayer.getGameProfile().getName();
                        if (rawName == null || rawName.isEmpty()) continue;

                        String lowerName = rawName.toLowerCase().trim();
                        this.markedPlayerNames.add(lowerName);

                        if (this.chatAlert.getValue() && !this.alertedPlayerNames.contains(lowerName)) {
                            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                                    "§c[Warning] §f玩家 §e" + rawName + " §f曾手持钻石剑，已被全网永久锁定！"
                            ));
                            this.alertedPlayerNames.add(lowerName);

                            this.sendNextMessage(rawName);
                        }
                    }
                }
            }
        }

        // 2. 拦截服务器系统聊天返回
        String chatMessage = "";
        if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            chatMessage = packet.content().getString();
        } else if (event.getPacket() instanceof ClientboundDisguisedChatPacket packet) {
            chatMessage = packet.message().getString();
        }

        if (!chatMessage.isEmpty()) {
            if (chatMessage.contains("请不要刷屏或者发送重复消息哦")) {
                Pattern pattern = Pattern.compile("\\((\\d+)\\s*秒\\)");
                Matcher matcher = pattern.matcher(chatMessage);
                if (matcher.find()) {
                    try {
                        int seconds = Integer.parseInt(matcher.group(1));
                        this.retryTime = System.currentTimeMillis() + (seconds + 1) * 1000L;
                    } catch (Exception e) {
                        this.retryTime = System.currentTimeMillis() + 2000L;
                    }
                }
            }

            if (chatMessage.contains("匹配") || chatMessage.contains("游戏开始") || chatMessage.contains("START")) {
                this.clearMarkers();
                this.pendingMessage = "";
                this.retryTime = 0;

                mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                        "§a[SwordNotifier] §f检测到新对局，已重置黑名单并强行终止挂起的重发任务。"
                ));
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        double nearestDistance = -1;
        String nearestName = "";

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String rawName = player.getGameProfile().getName();
            if (rawName == null) continue;

            if (!this.markedPlayerNames.contains(rawName.toLowerCase().trim())) continue;

            double distance = mc.player.distanceTo(player);
            if (nearestDistance < 0 || distance < nearestDistance) {
                nearestDistance = distance;
                nearestName = rawName;
            }
        }

        // 自动冷却重发原版包
        if (this.sendToPublicChat.getValue() && !this.pendingMessage.isEmpty() && this.retryTime > 0) {
            if (System.currentTimeMillis() >= this.retryTime) {
                this.sendNativeChatMessage(this.pendingMessage);
                this.retryTime = 0;
                this.pendingMessage = "";
            }
        }

        if (nearestDistance < 0) return;

        // 🛠️ 核心修改：只闪四周 2/5 面积，且边缘向内实现平滑渐变
        if (this.screenFlash.getValue() && nearestDistance <= 30.0) {
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();

            double speed = 1.0 + (30.0 - nearestDistance) * 0.4;
            float alphaAlpha = (float) ((Math.sin(System.currentTimeMillis() * 0.005 * speed) + 1.0) / 2.0);

            if (nearestDistance < 6.0) alphaAlpha = 0.45f;
            else alphaAlpha *= 0.35f;

            if (alphaAlpha > 0.01f) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

                com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
                com.mojang.blaze3d.vertex.BufferBuilder bufferbuilder = tesselator.getBuilder();
                com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

                int maxAlphaInt = (int)(alphaAlpha * 255);
                org.joml.Matrix4f matrix = event.poseStack().last().pose();

                // 📐 计算 2/5 (40%) 边缘界限
                float borderX = sw * 0.4f;
                float borderY = sh * 0.4f;

                bufferbuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);

                // 1. 顶部边缘渐变 (上边缘向下过渡 2/5)
                bufferbuilder.vertex(matrix, 0.0F, borderY, 0.0F).color(255, 0, 0, 0).endVertex();
                bufferbuilder.vertex(matrix, (float)sw, borderY, 0.0F).color(255, 0, 0, 0).endVertex();
                bufferbuilder.vertex(matrix, (float)sw, 0.0F, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();
                bufferbuilder.vertex(matrix, 0.0F, 0.0F, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();

                // 2. 底部边缘渐变 (下边缘向上过渡 2/5)
                bufferbuilder.vertex(matrix, 0.0F, (float)sh, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();
                bufferbuilder.vertex(matrix, (float)sw, (float)sh, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();
                bufferbuilder.vertex(matrix, (float)sw, (float)sh - borderY, 0.0F).color(255, 0, 0, 0).endVertex();
                bufferbuilder.vertex(matrix, 0.0F, (float)sh - borderY, 0.0F).color(255, 0, 0, 0).endVertex();

                // 3. 左侧边缘渐变 (左边缘向右过渡 2/5)
                bufferbuilder.vertex(matrix, 0.0F, (float)sh, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();
                bufferbuilder.vertex(matrix, borderX, (float)sh, 0.0F).color(255, 0, 0, 0).endVertex();
                bufferbuilder.vertex(matrix, borderX, 0.0F, 0.0F).color(255, 0, 0, 0).endVertex();
                bufferbuilder.vertex(matrix, 0.0F, 0.0F, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();

                // 4. 右侧边缘渐变 (右边缘向左过渡 2/5)
                bufferbuilder.vertex(matrix, (float)sw - borderX, (float)sh, 0.0F).color(255, 0, 0, 0).endVertex();
                bufferbuilder.vertex(matrix, (float)sw, (float)sh, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();
                bufferbuilder.vertex(matrix, (float)sw, 0.0F, 0.0F).color(255, 0, 0, maxAlphaInt).endVertex();
                bufferbuilder.vertex(matrix, (float)sw - borderX, 0.0F, 0.0F).color(255, 0, 0, 0).endVertex();

                tesselator.end();
            }
        }

        // 准星下方危险距离文本
        if (this.distanceText.getValue()) {
            String text = String.format("⚠ 危险目标 [%s] 距你 %.1f 格 ⚠", nearestName, nearestDistance);

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            float x = screenWidth / 2.0f;
            float y = screenHeight * 0.65f;

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

            if (event.poseStack() != null) {
                FontStore.PINGFANG_18.drawStringCenteredColor(
                        event.poseStack(),
                        text,
                        x,
                        y,
                        new java.awt.Color(255, 30, 30)
                );
            }
        }
    }
}