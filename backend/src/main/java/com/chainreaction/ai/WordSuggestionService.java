package com.chainreaction.ai;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;
import com.chainreaction.room.domain.WritingStyle;

@Service
public class WordSuggestionService {

    private static final Map<WritingStyle, List<String>> WORDS_BY_STYLE = new EnumMap<>(WritingStyle.class);
    private static final List<String> FALLBACK_WORDS = List.of("spark", "lantern", "moon", "button", "river");

    static {
        WORDS_BY_STYLE.put(WritingStyle.FUNNY, List.of("pickle", "kazoo", "waffle", "mustache", "banana"));
        WORDS_BY_STYLE.put(WritingStyle.HORROR, List.of("shadow", "mirror", "whisper", "cellar", "fog"));
        WORDS_BY_STYLE.put(WritingStyle.BATSHIT_CRAZY, List.of("volcano", "llama", "thunder", "glitter", "portal"));
        WORDS_BY_STYLE.put(WritingStyle.DETECTIVE_NOIR, List.of("alibi", "raincoat", "cipher", "briefcase", "fedora"));
        WORDS_BY_STYLE.put(WritingStyle.FAMILY_FRIENDLY, List.of("cupcake", "rainbow", "puzzle", "balloon", "pajamas"));
        WORDS_BY_STYLE.put(WritingStyle.DARK_HUMOR, List.of("umbrella", "deadline", "portrait", "receipt", "elevator"));
        WORDS_BY_STYLE.put(WritingStyle.SCI_FI, List.of("nebula", "android", "quantum", "terraform", "signal"));
        WORDS_BY_STYLE.put(WritingStyle.ROMANCE, List.of("letter", "rain", "promise", "garden", "moonlight"));
        WORDS_BY_STYLE.put(WritingStyle.EPIC, List.of("oath", "citadel", "banner", "oracle", "thunder"));
        WORDS_BY_STYLE.put(WritingStyle.CREEPY, List.of("hallway", "portrait", "whisper", "attic", "footstep"));
        WORDS_BY_STYLE.put(WritingStyle.POETIC_PROSE, List.of("river", "lantern", "ember", "horizon", "silence"));
        WORDS_BY_STYLE.put(WritingStyle.HOMER, List.of("oar", "bronze", "harbor", "feast", "oracle"));
        WORDS_BY_STYLE.put(WritingStyle.WILLIAM_SHAKESPEARE, List.of("crown", "dagger", "moon", "mask", "letter"));
        WORDS_BY_STYLE.put(WritingStyle.EDGAR_ALLAN_POE, List.of("raven", "chamber", "heartbeat", "tomb", "midnight"));
        WORDS_BY_STYLE.put(WritingStyle.OSCAR_WILDE, List.of("salon", "mirror", "orchid", "secret", "portrait"));
        WORDS_BY_STYLE.put(WritingStyle.NIKOLAI_GOGOL, List.of("overcoat", "clerk", "stamp", "nose", "bureau"));
        WORDS_BY_STYLE.put(WritingStyle.MIGUEL_DE_CERVANTES, List.of("windmill", "helmet", "inn", "road", "squire"));
        WORDS_BY_STYLE.put(WritingStyle.CHAT_CONVERSATION, List.of("night", "life", "today", "home", "plan"));
        WORDS_BY_STYLE.put(WritingStyle.IMPROVISED_THEATRE, List.of("thief", "guest", "hero", "hate", "love"));
    }

    private final WordModerationService wordModerationService;
    private final WordSuggestionPromptBuilder promptBuilder;
    private final Map<WritingStyle, List<String>> wordsByStyle;
    private final List<String> fallbackWords;

    @Autowired
    public WordSuggestionService(
            WordModerationService wordModerationService,
            WordSuggestionPromptBuilder promptBuilder) {
        this(wordModerationService, promptBuilder, WORDS_BY_STYLE, FALLBACK_WORDS);
    }

    WordSuggestionService(
            WordModerationService wordModerationService,
            WordSuggestionPromptBuilder promptBuilder,
            Map<WritingStyle, List<String>> wordsByStyle,
            List<String> fallbackWords) {
        this.wordModerationService = wordModerationService;
        this.promptBuilder = promptBuilder;
        this.wordsByStyle = wordsByStyle;
        this.fallbackWords = fallbackWords;
    }

    public WordSuggestionResult suggest(WordSuggestionRequest request) {
        WordSuggestionPrompt prompt = promptBuilder.build(request);
        List<String> candidates = wordsByStyle.getOrDefault(request.writingStyle(), fallbackWords);
        Set<String> previousWords = request.previousWords().stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        int start = Math.floorMod(prompt.userPrompt().hashCode(), candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            String candidate = candidates.get((start + offset) % candidates.size());
            ModeratedWord moderatedWord = moderateCandidate(candidate, request);
            if (moderatedWord == null) {
                continue;
            }
            if (!previousWords.contains(moderatedWord.normalized())) {
                return new WordSuggestionResult(
                        moderatedWord.original(),
                        moderatedWord.normalized(),
                        request.safetyMode().name());
            }
        }

        for (String fallbackWord : fallbackWords) {
            ModeratedWord fallback = moderateCandidate(fallbackWord, request);
            if (fallback != null) {
                return new WordSuggestionResult(fallback.original(), fallback.normalized(), request.safetyMode().name());
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.BAD_GATEWAY,
                "Could not produce a safe word suggestion.");
    }

    private ModeratedWord moderateCandidate(String candidate, WordSuggestionRequest request) {
        try {
            return wordModerationService.moderate(candidate, request.safetyMode());
        } catch (ApiException exception) {
            return null;
        }
    }
}
