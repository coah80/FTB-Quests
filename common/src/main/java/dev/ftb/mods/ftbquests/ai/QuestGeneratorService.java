package dev.ftb.mods.ftbquests.ai;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.FTBQuests;
import dev.ftb.mods.ftbquests.net.CreateObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for generating quests using the Gemini AI API.
 * This handles the conversion of AI-generated content to actual FTB Quests objects.
 */
public class QuestGeneratorService {
    
    private final GeminiApiClient apiClient;
    
    public QuestGeneratorService(String apiKey) {
        this.apiClient = new GeminiApiClient(apiKey);
    }
    
    public QuestGeneratorService() {
        this(GeminiConfig.API_KEY.get());
    }
    
    /**
     * Check if the Gemini API is configured with a valid API key.
     */
    public boolean isConfigured() {
        return apiClient.isConfigured();
    }
    
    /**
     * Test the API connection.
     */
    public CompletableFuture<Boolean> testConnection() {
        return apiClient.testConnection();
    }
    
    /**
     * Generate a quest line for an existing chapter.
     * 
     * @param chapter The chapter to add quests to
     * @param modpackName The name of the modpack
     * @param modList List of mods in the modpack
     * @param theme The theme for the quest line
     * @param questCount Number of quests to generate
     * @param progressCallback Called with progress updates
     * @param completionCallback Called when generation is complete
     */
    public void generateQuestLine(
            Chapter chapter,
            String modpackName,
            List<String> modList,
            String theme,
            int questCount,
            Consumer<Component> progressCallback,
            Consumer<GenerationResult> completionCallback) {
        
        if (!isConfigured()) {
            completionCallback.accept(new GenerationResult(false, 
                    Component.translatable("ftbquests.ai.error.no_api_key"), 
                    0));
            return;
        }
        
        progressCallback.accept(Component.translatable("ftbquests.ai.generating"));
        
        apiClient.generateQuestLine(modpackName, modList, theme, questCount)
                .thenAccept(result -> {
                    if (result.isPresent()) {
                        GeminiApiClient.GeneratedQuestLine questLine = result.get();
                        int createdCount = createQuestsFromGenerated(chapter, questLine, progressCallback);
                        completionCallback.accept(new GenerationResult(true, 
                                Component.translatable("ftbquests.ai.success", createdCount), 
                                createdCount));
                    } else {
                        completionCallback.accept(new GenerationResult(false, 
                                Component.translatable("ftbquests.ai.error.generation_failed"), 
                                0));
                    }
                })
                .exceptionally(throwable -> {
                    FTBQuests.LOGGER.error("Error generating quests", throwable);
                    completionCallback.accept(new GenerationResult(false, 
                            Component.translatable("ftbquests.ai.error.exception", throwable.getMessage()), 
                            0));
                    return null;
                });
    }
    
    /**
     * Generate a new chapter with quests.
     * 
     * @param modpackName The name of the modpack
     * @param modList List of mods in the modpack
     * @param theme The theme for the chapter
     * @param questCount Number of quests to generate
     * @param progressCallback Called with progress updates
     * @param completionCallback Called when generation is complete with the chapter data
     */
    public void generateNewChapterWithQuests(
            String modpackName,
            List<String> modList,
            String theme,
            int questCount,
            Consumer<Component> progressCallback,
            Consumer<ChapterGenerationResult> completionCallback) {
        
        if (!isConfigured()) {
            completionCallback.accept(new ChapterGenerationResult(false, 
                    Component.translatable("ftbquests.ai.error.no_api_key"), 
                    null));
            return;
        }
        
        progressCallback.accept(Component.translatable("ftbquests.ai.generating"));
        
        apiClient.generateQuestLine(modpackName, modList, theme, questCount)
                .thenAccept(result -> {
                    if (result.isPresent()) {
                        completionCallback.accept(new ChapterGenerationResult(true, 
                                Component.translatable("ftbquests.ai.success", result.get().quests.size()), 
                                result.get()));
                    } else {
                        completionCallback.accept(new ChapterGenerationResult(false, 
                                Component.translatable("ftbquests.ai.error.generation_failed"), 
                                null));
                    }
                })
                .exceptionally(throwable -> {
                    FTBQuests.LOGGER.error("Error generating chapter", throwable);
                    completionCallback.accept(new ChapterGenerationResult(false, 
                            Component.translatable("ftbquests.ai.error.exception", throwable.getMessage()), 
                            null));
                    return null;
                });
    }
    
    private int createQuestsFromGenerated(Chapter chapter, GeminiApiClient.GeneratedQuestLine questLine, Consumer<Component> progressCallback) {
        int created = 0;
        Map<Integer, Quest> createdQuests = new HashMap<>();
        
        // Calculate positions in a spiral pattern
        double startX = 0;
        double startY = 0;
        double spacing = 2.0;
        
        for (int i = 0; i < questLine.quests.size(); i++) {
            GeminiApiClient.GeneratedQuest genQuest = questLine.quests.get(i);
            
            progressCallback.accept(Component.translatable("ftbquests.ai.creating_quest", i + 1, questLine.quests.size()));
            
            try {
                // Create quest
                Quest quest = new Quest(0L, chapter);
                
                // Calculate position (simple horizontal layout with dependency-based Y offset)
                double xPos = startX + (i * spacing);
                double yPos = startY;
                if (!genQuest.dependencyIndices.isEmpty()) {
                    int firstDep = genQuest.dependencyIndices.get(0);
                    if (createdQuests.containsKey(firstDep)) {
                        Quest depQuest = createdQuests.get(firstDep);
                        xPos = depQuest.getX() + spacing;
                        yPos = depQuest.getY();
                    }
                }
                quest.setX(xPos);
                quest.setY(yPos);
                
                // Set quest icon based on first task
                if (!genQuest.tasks.isEmpty()) {
                    ItemStack iconStack = getItemStack(genQuest.tasks.get(0).item, 1);
                    quest.setRawIcon(ItemIcon.getItemIcon(iconStack));
                }
                
                // Create the quest object message with translation data
                CompoundTag extra = new CompoundTag();
                addTranslationToExtra(extra, chapter.file.getLocale(), TranslationKey.TITLE, genQuest.title);
                if (!genQuest.subtitle.isEmpty()) {
                    addTranslationToExtra(extra, chapter.file.getLocale(), TranslationKey.QUEST_SUBTITLE, genQuest.subtitle);
                }
                if (!genQuest.description.isEmpty()) {
                    addTranslationListToExtra(extra, chapter.file.getLocale(), TranslationKey.QUEST_DESC, genQuest.description);
                }
                
                // Send create quest message
                NetworkManager.sendToServer(CreateObjectMessage.create(quest, extra, false));
                
                createdQuests.put(i, quest);
                created++;
                
                // Note: Tasks and rewards would need to be created in follow-up messages
                // after the quest is confirmed created by the server
                
            } catch (Exception e) {
                FTBQuests.LOGGER.error("Error creating quest: {}", genQuest.title, e);
            }
        }
        
        return created;
    }
    
    private void addTranslationToExtra(CompoundTag extra, String locale, TranslationKey key, String value) {
        CompoundTag translations = extra.getCompound("translations");
        CompoundTag localeTag = translations.getCompound(locale);
        localeTag.putString(key.getName(), value);
        translations.put(locale, localeTag);
        extra.put("translations", translations);
    }
    
    private void addTranslationListToExtra(CompoundTag extra, String locale, TranslationKey key, List<String> values) {
        CompoundTag translations = extra.getCompound("translations");
        CompoundTag localeTag = translations.getCompound(locale);
        CompoundTag listTag = new CompoundTag();
        for (int i = 0; i < values.size(); i++) {
            listTag.putString(String.valueOf(i), values.get(i));
        }
        localeTag.put(key.getName(), listTag);
        translations.put(locale, localeTag);
        extra.put("translations", translations);
    }
    
    private ItemStack getItemStack(String itemId, int count) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            return new ItemStack(BuiltInRegistries.ITEM.get(rl), count);
        } catch (Exception e) {
            FTBQuests.LOGGER.warn("Invalid item ID: {}, using dirt", itemId);
            return new ItemStack(Items.DIRT, count);
        }
    }
    
    public record GenerationResult(boolean success, Component message, int questCount) {}
    
    public record ChapterGenerationResult(boolean success, Component message, GeminiApiClient.GeneratedQuestLine questLine) {}
}
