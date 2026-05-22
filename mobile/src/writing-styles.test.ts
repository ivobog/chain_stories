import { describe, expect, it } from "vitest";

import { STYLE_LABELS, WRITING_STYLES, WRITING_STYLE_GROUPS, styleLabel } from "./writing-styles";

describe("writing style catalog", () => {
  it("contains the approved 17-style catalog in order", () => {
    expect(WRITING_STYLES).toEqual([
      "FUNNY",
      "HORROR",
      "BATSHIT_CRAZY",
      "DETECTIVE_NOIR",
      "FAMILY_FRIENDLY",
      "DARK_HUMOR",
      "SCI_FI",
      "ROMANCE",
      "EPIC",
      "CREEPY",
      "POETIC_PROSE",
      "HOMER",
      "WILLIAM_SHAKESPEARE",
      "EDGAR_ALLAN_POE",
      "OSCAR_WILDE",
      "NIKOLAI_GOGOL",
      "MIGUEL_DE_CERVANTES",
      "CHAT_CONVERSATION",
      "IMPROVISED_THEATRE",
    ]);
  });

  it("groups the selector into core and classic author styles", () => {
    expect(WRITING_STYLE_GROUPS).toEqual([
      {
        label: "Core styles",
        options: [
          "FUNNY",
          "HORROR",
          "BATSHIT_CRAZY",
          "DETECTIVE_NOIR",
          "FAMILY_FRIENDLY",
          "DARK_HUMOR",
          "SCI_FI",
          "ROMANCE",
          "EPIC",
          "CREEPY",
          "POETIC_PROSE",
          "CHAT_CONVERSATION",
          "IMPROVISED_THEATRE",
        ],
      },
      {
        label: "Classic author styles",
        options: [
          "HOMER",
          "WILLIAM_SHAKESPEARE",
          "EDGAR_ALLAN_POE",
          "OSCAR_WILDE",
          "NIKOLAI_GOGOL",
          "MIGUEL_DE_CERVANTES",
        ],
      },
    ]);
  });

  it("keeps labels in sync with the flattened catalog", () => {
    expect(Object.keys(STYLE_LABELS)).toEqual([...WRITING_STYLES]);
    expect(styleLabel("DETECTIVE_NOIR")).toBe("Detective noir");
    expect(styleLabel("WILLIAM_SHAKESPEARE")).toBe("William Shakespeare");
    expect(styleLabel("EDGAR_ALLAN_POE")).toBe("Edgar Allan Poe");
    expect(styleLabel("MIGUEL_DE_CERVANTES")).toBe("Miguel de Cervantes");
  });
});
