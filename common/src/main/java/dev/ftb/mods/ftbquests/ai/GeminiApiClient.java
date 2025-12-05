package dev.ftb.mods.ftbquests.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftbquests.FTBQuests;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client for interacting with the Gemini API to generate quest content.
 * All API calls are made from the client side to keep the API key secure.
 */
public class GeminiApiClient {
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final String apiKey;
    private final String model;

    public GeminiApiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public GeminiApiClient(String apiKey) {
        this(apiKey, GeminiConfig.MODEL.get());
    }

    /**
     * Check if the API key is configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Generate quest line content based on the given modpack context.
     * 
     * @param modpackName The name of the modpack
     * @param modList List of mods in the modpack (can be empty)
     * @param chapterTheme The theme/topic for the quest chapter
     * @param questCount Number of quests to generate
     * @return CompletableFuture containing the generated quest data
     */
    public CompletableFuture<Optional<GeneratedQuestLine>> generateQuestLine(
            String modpackName, 
            List<String> modList, 
            String chapterTheme,
            int questCount) {
        
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String prompt = buildQuestGenerationPrompt(modpackName, modList, chapterTheme, questCount);
        
        return callGeminiApi(prompt)
                .thenApply(response -> response.flatMap(this::parseQuestLineResponse));
    }

    private String buildQuestGenerationPrompt(String modpackName, List<String> modList, String chapterTheme, int questCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Minecraft quest designer creating quests for the FTB Quests mod. ");
        sb.append("Generate a quest line for a Minecraft modpack with the following details:\n\n");
        
        sb.append("Modpack Name: ").append(modpackName).append("\n");
        
        if (!modList.isEmpty()) {
            sb.append("Mods included: ").append(String.join(", ", modList)).append("\n");
        }
        
        sb.append("Chapter Theme: ").append(chapterTheme).append("\n");
        sb.append("Number of Quests: ").append(questCount).append("\n\n");
        
        sb.append("Generate a JSON response with the following structure:\n");
        sb.append("{\n");
        sb.append("  \"chapter\": {\n");
        sb.append("    \"title\": \"Chapter Title\",\n");
        sb.append("    \"subtitle\": \"Brief chapter description\"\n");
        sb.append("  },\n");
        sb.append("  \"quests\": [\n");
        sb.append("    {\n");
        sb.append("      \"title\": \"Quest Title\",\n");
        sb.append("      \"subtitle\": \"Brief quest subtitle\",\n");
        sb.append("      \"description\": [\"Line 1 of description\", \"Line 2 of description\"],\n");
        sb.append("      \"tasks\": [\n");
        sb.append("        {\n");
        sb.append("          \"type\": \"item\",\n");
        sb.append("          \"item\": \"minecraft:item_id\",\n");
        sb.append("          \"count\": 1\n");
        sb.append("        }\n");
        sb.append("      ],\n");
        sb.append("      \"rewards\": [\n");
        sb.append("        {\n");
        sb.append("          \"type\": \"item\",\n");
        sb.append("          \"item\": \"minecraft:item_id\",\n");
        sb.append("          \"count\": 1\n");
        sb.append("        }\n");
        sb.append("      ],\n");
        sb.append("      \"dependencies\": [0]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        
        sb.append("Requirements:\n");
        sb.append("- Create ").append(questCount).append(" quests that form a logical progression\n");
        sb.append("- Use actual Minecraft item IDs (e.g., minecraft:diamond, minecraft:iron_ingot)\n");
        sb.append("- If mods are listed, include items from those mods when appropriate\n");
        sb.append("- Dependencies should reference quest indices (0-based)\n");
        sb.append("- First quest should have empty dependencies array\n");
        sb.append("- Task types can be: 'item' (collect/obtain items), 'kill' (kill entities), 'location' (visit location)\n");
        sb.append("- Make descriptions engaging and helpful for players\n");
        sb.append("- Ensure rewards are appropriate for the quest difficulty\n\n");
        sb.append("IMPORTANT: Return ONLY valid JSON, no markdown formatting or explanation text.");
        
        return sb.toString();
    }

    private CompletableFuture<Optional<String>> callGeminiApi(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                JsonArray contents = new JsonArray();
                JsonObject content = new JsonObject();
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                
                part.addProperty("text", prompt);
                parts.add(part);
                content.add("parts", parts);
                contents.add(content);
                requestBody.add("contents", contents);
                
                // Add generation config
                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("temperature", 0.7);
                generationConfig.addProperty("maxOutputTokens", 8192);
                requestBody.add("generationConfig", generationConfig);
                
                String url = String.format(GEMINI_API_URL, model, apiKey);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .build();
                
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (responseJson.has("candidates")) {
                        JsonArray candidates = responseJson.getAsJsonArray("candidates");
                        if (!candidates.isEmpty()) {
                            JsonObject candidate = candidates.get(0).getAsJsonObject();
                            if (candidate.has("content")) {
                                JsonObject contentObj = candidate.getAsJsonObject("content");
                                if (contentObj.has("parts")) {
                                    JsonArray partsArr = contentObj.getAsJsonArray("parts");
                                    if (!partsArr.isEmpty()) {
                                        String text = partsArr.get(0).getAsJsonObject().get("text").getAsString();
                                        return Optional.of(text);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    FTBQuests.LOGGER.error("Gemini API error: {} - {}", response.statusCode(), response.body());
                }
            } catch (IOException | InterruptedException e) {
                FTBQuests.LOGGER.error("Error calling Gemini API", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            return Optional.empty();
        });
    }

    private Optional<GeneratedQuestLine> parseQuestLineResponse(String response) {
        try {
            // Clean up the response - remove markdown code blocks if present
            String cleaned = response.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
            
            JsonObject json = JsonParser.parseString(cleaned).getAsJsonObject();
            return Optional.of(parseGeneratedQuestLine(json));
        } catch (Exception e) {
            FTBQuests.LOGGER.error("Error parsing Gemini response: {}", response, e);
            return Optional.empty();
        }
    }

    private GeneratedQuestLine parseGeneratedQuestLine(JsonObject json) {
        GeneratedQuestLine questLine = new GeneratedQuestLine();
        
        // Parse chapter info
        if (json.has("chapter")) {
            JsonObject chapter = json.getAsJsonObject("chapter");
            questLine.chapterTitle = chapter.has("title") ? chapter.get("title").getAsString() : "Generated Chapter";
            questLine.chapterSubtitle = chapter.has("subtitle") ? chapter.get("subtitle").getAsString() : "";
        }
        
        // Parse quests
        if (json.has("quests")) {
            JsonArray quests = json.getAsJsonArray("quests");
            for (int i = 0; i < quests.size(); i++) {
                JsonObject questJson = quests.get(i).getAsJsonObject();
                GeneratedQuest quest = new GeneratedQuest();
                
                quest.title = questJson.has("title") ? questJson.get("title").getAsString() : "Quest " + (i + 1);
                quest.subtitle = questJson.has("subtitle") ? questJson.get("subtitle").getAsString() : "";
                
                if (questJson.has("description")) {
                    JsonArray desc = questJson.getAsJsonArray("description");
                    for (int j = 0; j < desc.size(); j++) {
                        quest.description.add(desc.get(j).getAsString());
                    }
                }
                
                if (questJson.has("tasks")) {
                    JsonArray tasks = questJson.getAsJsonArray("tasks");
                    for (int j = 0; j < tasks.size(); j++) {
                        JsonObject taskJson = tasks.get(j).getAsJsonObject();
                        GeneratedTask task = new GeneratedTask();
                        task.type = taskJson.has("type") ? taskJson.get("type").getAsString() : "item";
                        task.item = taskJson.has("item") ? taskJson.get("item").getAsString() : "minecraft:dirt";
                        task.count = taskJson.has("count") ? taskJson.get("count").getAsInt() : 1;
                        quest.tasks.add(task);
                    }
                }
                
                if (questJson.has("rewards")) {
                    JsonArray rewards = questJson.getAsJsonArray("rewards");
                    for (int j = 0; j < rewards.size(); j++) {
                        JsonObject rewardJson = rewards.get(j).getAsJsonObject();
                        GeneratedReward reward = new GeneratedReward();
                        reward.type = rewardJson.has("type") ? rewardJson.get("type").getAsString() : "item";
                        reward.item = rewardJson.has("item") ? rewardJson.get("item").getAsString() : "minecraft:diamond";
                        reward.count = rewardJson.has("count") ? rewardJson.get("count").getAsInt() : 1;
                        quest.rewards.add(reward);
                    }
                }
                
                if (questJson.has("dependencies")) {
                    JsonArray deps = questJson.getAsJsonArray("dependencies");
                    for (int j = 0; j < deps.size(); j++) {
                        quest.dependencyIndices.add(deps.get(j).getAsInt());
                    }
                }
                
                questLine.quests.add(quest);
            }
        }
        
        return questLine;
    }

    /**
     * Test the API connection with a simple request.
     */
    public CompletableFuture<Boolean> testConnection() {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return callGeminiApi("Say 'Hello' in one word.")
                .thenApply(Optional::isPresent);
    }

    // Data classes for generated quest content
    public static class GeneratedQuestLine {
        public String chapterTitle = "";
        public String chapterSubtitle = "";
        public List<GeneratedQuest> quests = new ArrayList<>();
    }

    public static class GeneratedQuest {
        public String title = "";
        public String subtitle = "";
        public List<String> description = new ArrayList<>();
        public List<GeneratedTask> tasks = new ArrayList<>();
        public List<GeneratedReward> rewards = new ArrayList<>();
        public List<Integer> dependencyIndices = new ArrayList<>();
    }

    public static class GeneratedTask {
        public String type = "item";
        public String item = "";
        public int count = 1;
    }

    public static class GeneratedReward {
        public String type = "item";
        public String item = "";
        public int count = 1;
    }
}
