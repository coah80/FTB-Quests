package dev.ftb.mods.ftbquests.ai;

import dev.ftb.mods.ftblibrary.snbt.config.SNBTConfig;
import dev.ftb.mods.ftblibrary.snbt.config.StringValue;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;

/**
 * Configuration for Gemini AI integration.
 * The API key is stored locally on the client side for security.
 */
public interface GeminiConfig {
    String KEY = FTBQuestsAPI.MOD_ID + "-gemini";
    SNBTConfig CONFIG = SNBTConfig.create(KEY);

    SNBTConfig AI_SETTINGS = CONFIG.addGroup("ai_settings", 0);
    
    // API key for Gemini - stored locally, never sent to server
    StringValue API_KEY = AI_SETTINGS.addString("api_key", "");
    
    // Model to use for quest generation
    StringValue MODEL = AI_SETTINGS.addString("model", "gemini-2.0-flash-exp");
    
    // Maximum number of quests to generate per request
    StringValue MAX_QUESTS_PER_CHAPTER = AI_SETTINGS.addString("max_quests_per_chapter", "10");
}
