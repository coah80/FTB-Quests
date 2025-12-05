package dev.ftb.mods.ftbquests.client.gui;

import dev.ftb.mods.ftblibrary.config.manager.ConfigManager;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.*;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.ai.GeminiApiClient;
import dev.ftb.mods.ftbquests.ai.GeminiConfig;
import dev.ftb.mods.ftbquests.ai.QuestGeneratorService;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for configuring Gemini AI settings and generating quest lines.
 */
public class GeminiQuestGeneratorScreen extends BaseScreen {
    
    private final Chapter targetChapter;
    private TextBox apiKeyInput;
    private TextBox modpackNameInput;
    private TextBox themeInput;
    private TextBox modListInput;
    private TextBox questCountInput;
    private SimpleTextButton generateButton;
    private SimpleTextButton testButton;
    private Component statusMessage = Component.empty();
    private boolean isGenerating = false;
    
    public GeminiQuestGeneratorScreen(Chapter chapter) {
        this.targetChapter = chapter;
    }
    
    @Override
    public boolean onInit() {
        setWidth(300);
        setHeight(260);
        return true;
    }
    
    @Override
    public void addWidgets() {
        int y = 5;
        int labelWidth = 100;
        int inputWidth = width - labelWidth - 20;
        
        // Title
        add(new TextField(this).setText(Component.translatable("ftbquests.ai.title").withStyle(ChatFormatting.BOLD))
                .setPos(10, y));
        y += 20;
        
        // API Key section
        add(new TextField(this).setText(Component.translatable("ftbquests.ai.api_key"))
                .setPos(10, y));
        
        apiKeyInput = new TextBox(this) {
            @Override
            public void onTextChanged() {
                // Save API key when changed
                GeminiConfig.API_KEY.set(getText());
                ConfigManager.getInstance().save(GeminiConfig.KEY);
            }
        };
        apiKeyInput.setPos(labelWidth, y);
        apiKeyInput.setSize(inputWidth, 16);
        apiKeyInput.setText(GeminiConfig.API_KEY.get());
        apiKeyInput.ghostText = "Enter your Gemini API key...";
        add(apiKeyInput);
        y += 22;
        
        // Test Connection button
        testButton = new SimpleTextButton(this, Component.translatable("ftbquests.ai.test_connection"), Icons.ACCEPT) {
            @Override
            public void onClicked(MouseButton button) {
                testConnection();
            }
        };
        testButton.setPos(10, y);
        testButton.setSize(100, 16);
        add(testButton);
        y += 25;
        
        // Separator
        y += 5;
        
        // Modpack Name
        add(new TextField(this).setText(Component.translatable("ftbquests.ai.modpack_name"))
                .setPos(10, y));
        
        modpackNameInput = new TextBox(this);
        modpackNameInput.setPos(labelWidth, y);
        modpackNameInput.setSize(inputWidth, 16);
        modpackNameInput.setText("My Modpack");
        modpackNameInput.ghostText = "Enter modpack name...";
        add(modpackNameInput);
        y += 22;
        
        // Theme
        add(new TextField(this).setText(Component.translatable("ftbquests.ai.theme"))
                .setPos(10, y));
        
        themeInput = new TextBox(this);
        themeInput.setPos(labelWidth, y);
        themeInput.setSize(inputWidth, 16);
        themeInput.setText(targetChapter != null ? targetChapter.getTitle().getString() : "Getting Started");
        themeInput.ghostText = "Enter quest theme...";
        add(themeInput);
        y += 22;
        
        // Mod List (optional)
        add(new TextField(this).setText(Component.translatable("ftbquests.ai.mod_list"))
                .setPos(10, y));
        
        modListInput = new TextBox(this);
        modListInput.setPos(labelWidth, y);
        modListInput.setSize(inputWidth, 16);
        modListInput.ghostText = "mod1, mod2, mod3 (optional)";
        add(modListInput);
        y += 22;
        
        // Quest Count
        add(new TextField(this).setText(Component.translatable("ftbquests.ai.quest_count"))
                .setPos(10, y));
        
        questCountInput = new TextBox(this);
        questCountInput.setPos(labelWidth, y);
        questCountInput.setSize(50, 16);
        questCountInput.setText("5");
        add(questCountInput);
        y += 30;
        
        // Generate Button
        generateButton = new SimpleTextButton(this, Component.translatable("ftbquests.ai.generate"), Icons.ADD) {
            @Override
            public void onClicked(MouseButton button) {
                if (!isGenerating) {
                    startGeneration();
                }
            }
            
            @Override
            public void addMouseOverText(TooltipList list) {
                if (targetChapter == null) {
                    list.add(Component.translatable("ftbquests.ai.tooltip.no_chapter").withStyle(ChatFormatting.RED));
                } else if (apiKeyInput.getText().isEmpty()) {
                    list.add(Component.translatable("ftbquests.ai.tooltip.no_api_key").withStyle(ChatFormatting.RED));
                } else {
                    list.add(Component.translatable("ftbquests.ai.tooltip.generate", targetChapter.getTitle()));
                }
            }
        };
        generateButton.setPos((width - 120) / 2, y);
        generateButton.setSize(120, 20);
        add(generateButton);
        y += 25;
        
        // Status message area
        add(new TextField(this) {
            @Override
            public Component getTitle() {
                return statusMessage;
            }
        }.setPos(10, y));
    }
    
    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        theme.drawGui(graphics, x, y, w, h, WidgetType.NORMAL);
    }
    
    private void testConnection() {
        if (apiKeyInput.getText().isEmpty()) {
            statusMessage = Component.translatable("ftbquests.ai.error.no_api_key").withStyle(ChatFormatting.RED);
            return;
        }
        
        statusMessage = Component.translatable("ftbquests.ai.testing").withStyle(ChatFormatting.YELLOW);
        testButton.setTitle(Component.translatable("ftbquests.ai.testing"));
        
        QuestGeneratorService service = new QuestGeneratorService(apiKeyInput.getText());
        service.testConnection().thenAccept(success -> {
            Minecraft.getInstance().execute(() -> {
                if (success) {
                    statusMessage = Component.translatable("ftbquests.ai.test_success").withStyle(ChatFormatting.GREEN);
                } else {
                    statusMessage = Component.translatable("ftbquests.ai.test_failed").withStyle(ChatFormatting.RED);
                }
                testButton.setTitle(Component.translatable("ftbquests.ai.test_connection"));
            });
        });
    }
    
    private void startGeneration() {
        if (targetChapter == null) {
            statusMessage = Component.translatable("ftbquests.ai.error.no_chapter").withStyle(ChatFormatting.RED);
            return;
        }
        
        if (apiKeyInput.getText().isEmpty()) {
            statusMessage = Component.translatable("ftbquests.ai.error.no_api_key").withStyle(ChatFormatting.RED);
            return;
        }
        
        int questCount;
        try {
            questCount = Integer.parseInt(questCountInput.getText());
            if (questCount < 1 || questCount > 50) {
                statusMessage = Component.translatable("ftbquests.ai.error.invalid_count").withStyle(ChatFormatting.RED);
                return;
            }
        } catch (NumberFormatException e) {
            statusMessage = Component.translatable("ftbquests.ai.error.invalid_count").withStyle(ChatFormatting.RED);
            return;
        }
        
        isGenerating = true;
        generateButton.setTitle(Component.translatable("ftbquests.ai.generating"));
        
        List<String> modList = new ArrayList<>();
        String modListText = modListInput.getText().trim();
        if (!modListText.isEmpty()) {
            for (String mod : modListText.split(",")) {
                modList.add(mod.trim());
            }
        }
        
        QuestGeneratorService service = new QuestGeneratorService(apiKeyInput.getText());
        service.generateQuestLine(
                targetChapter,
                modpackNameInput.getText(),
                modList,
                themeInput.getText(),
                questCount,
                progress -> Minecraft.getInstance().execute(() -> statusMessage = progress.copy().withStyle(ChatFormatting.YELLOW)),
                result -> Minecraft.getInstance().execute(() -> {
                    isGenerating = false;
                    generateButton.setTitle(Component.translatable("ftbquests.ai.generate"));
                    statusMessage = result.message().copy().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED);
                    
                    if (result.success()) {
                        // Refresh the quest screen
                        ClientQuestFile.INSTANCE.refreshGui();
                    }
                })
        );
    }
    
    @Override
    public Component getTitle() {
        return Component.translatable("ftbquests.ai.title");
    }
    
    @Override
    public Theme getTheme() {
        return FTBQuestsTheme.INSTANCE;
    }
}
