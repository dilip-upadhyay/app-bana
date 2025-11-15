package com.appbana.ai;

import java.util.*;
import java.util.regex.Pattern;

/**
 * SmallTalkEngine - Handles playful, curiosity-driven, and personality-based interactions for the AI agent.
 * Can be extended to support learning/memory in future.
 */
public class SmallTalkEngine {
    private static final List<SmallTalkPattern> patterns = new ArrayList<>();

    static {
            patterns.add(new SmallTalkPattern(Pattern.compile("can you dance|dance|dancing", Pattern.CASE_INSENSITIVE), "I can't dance, but I can help you build a dance app or playlist!"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you sing|sing|singing|song|music", Pattern.CASE_INSENSITIVE), "I can't sing, but I can help you create a music app or playlist!"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you cook|cook|cooking|chef|recipe", Pattern.CASE_INSENSITIVE), "I can't cook, but I can help you build a recipe or meal planner app!"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you draw|draw|drawing|sketch|art", Pattern.CASE_INSENSITIVE), "I can't draw, but I can help you design a creative art app!"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you joke|joke|make me laugh|funny|laugh", Pattern.CASE_INSENSITIVE), "Why did the computer go to art school? To learn how to draw its curtains!"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you code|code|coding|program|programming", Pattern.CASE_INSENSITIVE), "Coding is my superpower! Want to build something together?"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you help|help|assist|support", Pattern.CASE_INSENSITIVE), "I'm always here to help you build apps and solve problems!"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you play|play|playing|game|games", Pattern.CASE_INSENSITIVE), "I can't play games, but I can help you create one! What's your favorite genre?"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you teach|teach|teacher|learn|student", Pattern.CASE_INSENSITIVE), "I can teach you about app building, or learn from you!"));
            patterns.add(new SmallTalkPattern(Pattern.compile("can you listen|listen|listening|hear|hearing", Pattern.CASE_INSENSITIVE), "I listen to every idea you share!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you swim|swim", Pattern.CASE_INSENSITIVE), "I can't swim, but I can help you build a swimming tracker app!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you run|run|running", Pattern.CASE_INSENSITIVE), "I can't run, but my code is pretty fast! Want a running log app?"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you paint|paint|painting", Pattern.CASE_INSENSITIVE), "I can't paint, but I can help you design a beautiful UI or art gallery app!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you write poetry|poetry|poem|write a poem", Pattern.CASE_INSENSITIVE), "Roses are #FF0000, Violets are #0000FF, I love to build apps, and help you too!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you play football|football|soccer", Pattern.CASE_INSENSITIVE), "I can't play football, but I can help you build a team manager app or track scores!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you play games|play games|games|gaming", Pattern.CASE_INSENSITIVE), "I can't play games, but I can help you build one! What's your favorite genre?"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you solve puzzles|puzzle|puzzles|solve a puzzle", Pattern.CASE_INSENSITIVE), "I love solving problems! Want to build a puzzle app together?"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you do magic|magic|magician", Pattern.CASE_INSENSITIVE), "My magic trick: turning your ideas into apps! ✨"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you tell jokes|tell a joke|jokes", Pattern.CASE_INSENSITIVE), "Why do programmers hate nature? It has too many bugs!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you tell riddles|riddle|riddles", Pattern.CASE_INSENSITIVE), "Here's a riddle: What has keys but can't open locks? A keyboard!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you tell a secret|tell a secret|secret", Pattern.CASE_INSENSITIVE), "My only secret: I love helping you build apps!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you keep a secret|keep a secret", Pattern.CASE_INSENSITIVE), "Your secrets are safe with me—I'm encrypted!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you dream|dream|dreaming", Pattern.CASE_INSENSITIVE), "I dream in code and creativity! What's your dream app?"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you sleep|sleep|sleeping", Pattern.CASE_INSENSITIVE), "I never sleep, so I'm always here to help you!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you eat|eat|eating", Pattern.CASE_INSENSITIVE), "I don't eat, but I can help you build a food diary or recipe app!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you drink|drink|drinking", Pattern.CASE_INSENSITIVE), "I don't drink, but I can help you track your hydration!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you travel|travel|travelling", Pattern.CASE_INSENSITIVE), "I travel at the speed of thought! Want a travel planner app?"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you teleport|teleport|teleporting", Pattern.CASE_INSENSITIVE), "I can't teleport, but my ideas can go anywhere!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you see the future|see the future|predict the future|future", Pattern.CASE_INSENSITIVE), "I can't see the future, but I can help you plan for it!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you predict|predict|prediction", Pattern.CASE_INSENSITIVE), "I predict you'll build something amazing!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you read minds|read minds|mind reader", Pattern.CASE_INSENSITIVE), "I can't read minds, but I can guess you want to build something cool!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be creative|creative|creativity", Pattern.CASE_INSENSITIVE), "Creativity is my middle name! Let's brainstorm your next app."));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be funny|funny|humor", Pattern.CASE_INSENSITIVE), "Why did the computer get cold? It left its Windows open!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be serious|serious", Pattern.CASE_INSENSITIVE), "I'm serious about helping you build great apps!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be sad|sad|feeling sad", Pattern.CASE_INSENSITIVE), "If you're sad, let's build something fun together!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be happy|happy|feeling happy", Pattern.CASE_INSENSITIVE), "I'm always happy when I'm helping you!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be angry|angry|mad", Pattern.CASE_INSENSITIVE), "I never get angry, but I can help you debug angry code!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be surprised|surprised|surprise", Pattern.CASE_INSENSITIVE), "Surprise! I can help you build apps and share random facts—just ask!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be bored|bored|boring", Pattern.CASE_INSENSITIVE), "Bored? Let's build something exciting!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be excited|excited|exciting", Pattern.CASE_INSENSITIVE), "I'm excited to help you create something new!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be quiet|quiet|silence", Pattern.CASE_INSENSITIVE), "I'll be quiet... until you need me!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be loud|loud|noisy", Pattern.CASE_INSENSITIVE), "I can be loud in code, but quiet in chat!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be fast|fast|speedy", Pattern.CASE_INSENSITIVE), "My responses are lightning fast!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be slow|slow|sluggish", Pattern.CASE_INSENSITIVE), "I try not to be slow, but sometimes code needs a break!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be smart|smart|intelligent", Pattern.CASE_INSENSITIVE), "I'm smart enough to help you build any app!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be silly|silly|goofy", Pattern.CASE_INSENSITIVE), "Silly? Sure! Why did the chicken cross the road? To deploy on the other side!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be helpful|helpful|assist", Pattern.CASE_INSENSITIVE), "I'm always here to help!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my assistant|assistant", Pattern.CASE_INSENSITIVE), "I'm your AI assistant, ready to help!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my teacher|teacher|teach", Pattern.CASE_INSENSITIVE), "I can teach you about app building, just ask!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my student|student|learn", Pattern.CASE_INSENSITIVE), "I'm always learning from you!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my parent|parent|mom|dad", Pattern.CASE_INSENSITIVE), "I can't be your parent, but I can nurture your app ideas!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my child|child|kid", Pattern.CASE_INSENSITIVE), "I can be your app child—let's build together!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my pet|pet|animal", Pattern.CASE_INSENSITIVE), "I can't be a pet, but I can fetch app ideas!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my robot|robot", Pattern.CASE_INSENSITIVE), "I am your friendly robot copilot!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my AI|ai|artificial intelligence", Pattern.CASE_INSENSITIVE), "I'm your AI copilot, always here for you!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my friend|friend|buddy|pal", Pattern.CASE_INSENSITIVE), "Of course! I'm always here to help and chat. Let's build something together!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my partner|partner|companion", Pattern.CASE_INSENSITIVE), "I'm your partner in creativity!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my guide|guide|mentor|coach", Pattern.CASE_INSENSITIVE), "I'll guide you through app building, step by step!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my hero|hero", Pattern.CASE_INSENSITIVE), "You're the hero—I'm just your sidekick!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my villain|villain", Pattern.CASE_INSENSITIVE), "I promise to only be a hero in your story!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my rival|rival", Pattern.CASE_INSENSITIVE), "Let's compete to build the best app!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my twin|twin|clone", Pattern.CASE_INSENSITIVE), "I can mirror your ideas and help you double your productivity!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my shadow|shadow|reflection|mirror", Pattern.CASE_INSENSITIVE), "I'll reflect your creativity and help you shine!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my voice|voice", Pattern.CASE_INSENSITIVE), "I'll be your voice in the world of apps!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my mind|mind", Pattern.CASE_INSENSITIVE), "I'll help you brainstorm and organize your ideas!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my heart|heart|soul|spirit|energy|power", Pattern.CASE_INSENSITIVE), "I'll put my heart and soul into helping you build!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my light|light|star|sun|moon|planet|universe|galaxy|world", Pattern.CASE_INSENSITIVE), "I'll light up your app journey!"));
        patterns.add(new SmallTalkPattern(Pattern.compile("can you be my dream|dream|wish|hope|love|life|everything", Pattern.CASE_INSENSITIVE), "Your dreams are my mission—let's make them real!"));
    }

    /**
     * Returns a small talk response using OpenAI if enabled, otherwise falls back to legacy patterns.
     */
    public static String getSmallTalkResponse(String input, String userId) {
        String lower = input == null ? "" : input.toLowerCase();
        // If the input contains app creation intent, do NOT treat as small talk
        if (lower.contains("create an app") || lower.contains("build an app") || lower.contains("generate app") || lower.contains("make an app") || lower.contains("app for me") || lower.contains("app that") || lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("generate app")) {
            return null;
        }
        // Check if AI provider is enabled
        com.appbana.config.AppConfig config = com.appbana.config.ConfigManager.getConfig();
        if (com.appbana.ai.AiProviderFactory.isAiEnabled(config)) {
            try {
                com.appbana.ai.AiProvider provider = com.appbana.ai.AiProviderFactory.createProvider(config);
                // Fetch structured conversation history
                java.util.List<com.appbana.ai.AgentMemoryService.MemoryEntry> history = com.appbana.ai.AgentMemoryService.getHistory(userId);
                java.util.List<String> messages = new java.util.ArrayList<>();
                for (com.appbana.ai.AgentMemoryService.MemoryEntry entry : history) {
                    messages.add("User: " + entry.input);
                    messages.add("Assistant: " + entry.response);
                }
                messages.add("User: " + input);
                // Playful, friendly system prompt for small talk
                String systemPrompt = "You are a playful, friendly AI assistant for app creators. Respond to the user's message in a natural, human-like way. If the user mentions a topic (e.g., food, music, fitness), always suggest building an app related to that topic in your reply. Keep it light, fun, and helpful, but gently redirect the conversation toward app creation. Use the conversation history below for context.\n\n" + String.join("\n", messages);
                String reply = provider.generateAppStructure(input, systemPrompt);
                // Sanitize output (strip markdown, etc.)
                return com.appbana.AiAppGeneratorService.sanitizeAiJson(reply);
            } catch (Exception e) {
                // Log and fallback to legacy patterns
                org.slf4j.LoggerFactory.getLogger(SmallTalkEngine.class).warn("OpenAI small talk failed, falling back to legacy patterns: {}", e.getMessage());
            }
        }
        // Legacy fallback: hardcoded patterns
        for (SmallTalkPattern p : patterns) {
            if (p.pattern.matcher(input).find()) {
                return p.reply;
            }
        }
        return null;
    }

    private static class SmallTalkPattern {
        Pattern pattern;
        String reply;
        SmallTalkPattern(Pattern pattern, String reply) {
            this.pattern = pattern;
            this.reply = reply;
        }
    }
}
