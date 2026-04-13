import { LocalStorageDraftStorage } from "./localStorageDraftStorage";
import type { ScenarioDraftStorage } from "./types";

export const draftStorage: ScenarioDraftStorage = new LocalStorageDraftStorage();
