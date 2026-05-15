import type { StorySegmentResponse } from "./api";

export interface StoryTextPart {
  text: string;
  bold: boolean;
}

export function splitByPlayedWord(text: string, playedWord?: string | null): StoryTextPart[] {
  const trimmedPlayedWord = playedWord?.trim();
  if (!trimmedPlayedWord) {
    return [{ text, bold: false }];
  }

  const pattern = new RegExp(
    `(^|[^\\p{L}\\p{N}'-])(${escapeRegExp(trimmedPlayedWord)})(?=$|[^\\p{L}\\p{N}'-])`,
    "giu",
  );
  const parts: StoryTextPart[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null = pattern.exec(text);

  while (match) {
    const prefix = match[1] ?? "";
    const matchedWord = match[2] ?? "";
    const wordStart = match.index + prefix.length;

    if (wordStart > lastIndex) {
      parts.push({ text: text.slice(lastIndex, wordStart), bold: false });
    }

    parts.push({ text: matchedWord, bold: true });
    lastIndex = wordStart + matchedWord.length;
    match = pattern.exec(text);
  }

  if (lastIndex < text.length) {
    parts.push({ text: text.slice(lastIndex), bold: false });
  }

  return parts.length > 0 ? parts : [{ text, bold: false }];
}

export function renderableStoryText(segment: StorySegmentResponse): StoryTextPart[] {
  return splitByPlayedWord(segment.content, segment.playedWord);
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
