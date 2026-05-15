import { describe, expect, it } from "vitest";

import { splitByPlayedWord } from "./story-text";

describe("splitByPlayedWord", () => {
  it("returns plain text when no played word exists", () => {
    expect(splitByPlayedWord("A simple sentence.", null)).toEqual([{ text: "A simple sentence.", bold: false }]);
  });

  it("bolds an exact played word match", () => {
    expect(splitByPlayedWord('The word "dragon" pushes the story on.', "dragon")).toEqual([
      { text: 'The word "', bold: false },
      { text: "dragon", bold: true },
      { text: '" pushes the story on.', bold: false },
    ]);
  });

  it("matches case-insensitively while preserving original text casing", () => {
    expect(splitByPlayedWord("Dragon fire filled the room.", "dragon")).toEqual([
      { text: "Dragon", bold: true },
      { text: " fire filled the room.", bold: false },
    ]);
  });

  it("does not bold a substring inside a larger word", () => {
    expect(splitByPlayedWord("The cathedral bell rang.", "cat")).toEqual([
      { text: "The cathedral bell rang.", bold: false },
    ]);
  });

  it("supports punctuation boundaries", () => {
    expect(splitByPlayedWord("Moon, moon! moon?", "moon")).toEqual([
      { text: "Moon", bold: true },
      { text: ", ", bold: false },
      { text: "moon", bold: true },
      { text: "! ", bold: false },
      { text: "moon", bold: true },
      { text: "?", bold: false },
    ]);
  });
});
