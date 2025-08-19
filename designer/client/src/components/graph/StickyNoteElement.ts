import { dia } from "jointjs";

import type { UserSettings } from "../../reducers/userSettings";
import { stickyNoteElementAdvanced } from "./EspNode/stickyNote/advancedStickyNoteConfig";
import { StickyNoteElementBasic } from "./EspNode/stickyNote/basicStickyNoteConfig";

import MarkupNodeJSON = dia.MarkupNodeJSON;

export interface StickyNoteDefaults {
    position?: { x: number; y: number };
    size?: { width: number; height: number };
    attrs?: Record<string, unknown>;
}

export interface StickyNoteProtoProps {
    markup: (dia.MarkupNodeJSON | MarkupNodeJSON)[];
    [key: string]: unknown;
}

export const StickyNoteElement = (defaults?: StickyNoteDefaults, protoProps?: StickyNoteProtoProps) =>
    dia.Element.define("stickyNote.StickyNoteElement", defaults, protoProps);

export const StickyNoteElementView = (userSettings: UserSettings) =>
    userSettings["node.advancedStickyNotes"] ? stickyNoteElementAdvanced : StickyNoteElementBasic;
