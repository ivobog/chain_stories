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
        WORDS_BY_STYLE.put(WritingStyle.NOIR_DETECTIVE, List.of("alibi", "cigarette", "raincoat", "cipher", "briefcase"));
        WORDS_BY_STYLE.put(WritingStyle.FAIRY_TALE, List.of("crown", "acorn", "wand", "goblet", "castle"));
        WORDS_BY_STYLE.put(WritingStyle.MANGA_ACTION, List.of("katana", "comet", "rival", "dojo", "tornado"));
        WORDS_BY_STYLE.put(WritingStyle.FAMILY_FRIENDLY, List.of("cupcake", "rainbow", "puzzle", "balloon", "pajamas"));
        WORDS_BY_STYLE.put(WritingStyle.SWISS_CHAOS, List.of("fondue", "tram", "yodel", "glacier", "cowbell"));
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
