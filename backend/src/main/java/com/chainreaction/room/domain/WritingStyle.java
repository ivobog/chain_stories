package com.chainreaction.room.domain;

public enum WritingStyle {
    FUNNY(
            "Funny",
            "Keep the story playful, light, and joke-forward. Use comic timing, surprising reversals, and witty exaggeration. Avoid mean-spirited humor or pure randomness."),
    HORROR(
            "Horror",
            "Build dread through atmosphere, pacing, and implication. Use suspense, sensory unease, and escalating tension. Avoid graphic gore, shock-only twists, and unsafe content."),
    BATSHIT_CRAZY(
            "Batshit crazy",
            "Create controlled absurdity: surreal jumps, impossible objects, chaotic energy, and wild reversals. Keep it coherent enough to follow, and avoid pure randomness or offensive chaos."),
    DETECTIVE_NOIR(
            "Detective noir",
            "Use hardboiled mystery atmosphere, sharp observations, rainy streets, secrets, alibis, and moral ambiguity. Keep the sentence stylish, tense, and clue-forward."),
    FAMILY_FRIENDLY(
            "Family friendly",
            "Keep the story warm, clear, safe, and inclusive. Use gentle humor, wonder, teamwork, and everyday adventure. Avoid adult themes, cruelty, profanity, and intense fear."),
    DARK_HUMOR(
            "Dark humor",
            "Use bleak irony, uncomfortable timing, and comic contrast around misfortune without targeting protected groups or trivializing self-harm, abuse, real tragedies, or suffering."),
    SCI_FI(
            "Sci-fi",
            "Use speculative technology, strange science, alien logic, future societies, and high-concept problems. Keep emotional stakes clear and avoid dense exposition."),
    ROMANCE(
            "Romance",
            "Focus on emotional tension, tenderness, longing, chemistry, vulnerability, and meaningful gestures. Keep it tasteful and compatible with the configured safety mode."),
    EPIC(
            "Epic",
            "Use grand stakes, heroic momentum, mythic scale, ceremonial language, quests, oaths, battles, and destiny. Keep it readable and avoid overblown repetition."),
    CREEPY(
            "Creepy",
            "Create quiet unease, uncanny details, liminal spaces, wrongness, and slow-building discomfort. Prefer implication over explicit horror or gore."),
    POETIC_PROSE(
            "Poetic prose",
            "Use lyrical rhythm, vivid sensory imagery, metaphor, and emotional resonance. Keep the sentence elegant and precise, not overly purple or confusing."),
    HOMER(
            "Homer",
            "Use public-domain ancient epic flavor: invocations, fate, gods, omens, heroic epithets, journeys, feasts, and battle imagery. Do not quote or imitate any modern copyrighted translation verbatim."),
    WILLIAM_SHAKESPEARE(
            "William Shakespeare",
            "Use public-domain Elizabethan theatrical flavor: wit, rhetorical turns, dramatic irony, heightened metaphor, and playful wordcraft. Avoid direct quotation and avoid unreadable pseudo-archaic clutter."),
    EDGAR_ALLAN_POE(
            "Edgar Allan Poe",
            "Use public-domain gothic intensity: obsession, melancholy, dread, unreliable perception, ornate cadence, and symbolic darkness. Avoid direct quotation or copied passages."),
    OSCAR_WILDE(
            "Oscar Wilde",
            "Use public-domain epigrammatic wit, polished social satire, paradox, elegance, and sparkling dialogue energy. Avoid direct quotation or copied aphorisms."),
    NIKOLAI_GOGOL(
            "Nikolai Gogol",
            "Use public-domain grotesque social comedy: absurd bureaucracy, anxious officials, surreal realism, awkward dignity, and escalating ridiculousness. Avoid direct quotation."),
    MIGUEL_DE_CERVANTES(
            "Miguel de Cervantes",
            "Use public-domain picaresque adventure: playful narration, noble delusion, comic dignity, travel, mishaps, and the tension between idealism and reality. Avoid direct quotation."),
	IMPROVISED_THEATRE(
			"Improvised_Theatre",
			"Write the story like an improvised theatre scene. React creatively to the inserted player word and make it feel important to the scene. Use energetic dialogue, quick reversals, emotional reactions, and theatrical action beats. Do not ignore the word. Do not force it awkwardly. Let it naturally twist the scene."),
    CHAT_CONVERSATION(
    		"Chat Conversation",
    		"Write the story as a chat conversation between characters. Use short messages, usernames, reactions, pauses, typos only when funny or meaningful, and escalating misunderstandings. Let the story unfold through messages rather than narration. Keep it fast, playful, and easy to read.");
	
	private final String displayLabel;
    private final String guidance;

    WritingStyle(String displayLabel, String guidance) {
        this.displayLabel = displayLabel;
        this.guidance = guidance;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public String guidance() {
        return guidance;
    }
}
