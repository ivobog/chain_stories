import type { WritingStyle } from "./api";

export interface WritingStyleGroup {
  label: string;
  options: readonly WritingStyle[];
}

const CORE_WRITING_STYLES = [
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
] as const satisfies readonly WritingStyle[];

const CLASSIC_AUTHOR_WRITING_STYLES = [
  "HOMER",
  "WILLIAM_SHAKESPEARE",
  "EDGAR_ALLAN_POE",
  "OSCAR_WILDE",
  "NIKOLAI_GOGOL",
  "MIGUEL_DE_CERVANTES",
] as const satisfies readonly WritingStyle[];

export const WRITING_STYLES = [...CORE_WRITING_STYLES, ...CLASSIC_AUTHOR_WRITING_STYLES] as const satisfies readonly WritingStyle[];

export const WRITING_STYLE_GROUPS = [
  {
    label: "Core styles",
    options: CORE_WRITING_STYLES,
  },
  {
    label: "Classic author styles",
    options: CLASSIC_AUTHOR_WRITING_STYLES,
  },
] as const satisfies readonly WritingStyleGroup[];

export const STYLE_LABELS: Record<WritingStyle, string> = {
  FUNNY: "Funny",
  HORROR: "Horror",
  BATSHIT_CRAZY: "Batshit crazy",
  DETECTIVE_NOIR: "Detective noir",
  FAMILY_FRIENDLY: "Family friendly",
  DARK_HUMOR: "Dark humor",
  SCI_FI: "Sci-fi",
  ROMANCE: "Romance",
  EPIC: "Epic",
  CREEPY: "Creepy",
  POETIC_PROSE: "Poetic prose",
  HOMER: "Homer",
  WILLIAM_SHAKESPEARE: "William Shakespeare",
  EDGAR_ALLAN_POE: "Edgar Allan Poe",
  OSCAR_WILDE: "Oscar Wilde",
  NIKOLAI_GOGOL: "Nikolai Gogol",
  MIGUEL_DE_CERVANTES: "Miguel de Cervantes",
};

export function styleLabel(style: WritingStyle) {
  return STYLE_LABELS[style];
}
