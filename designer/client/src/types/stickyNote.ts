import { ComponentDefinition } from "./scenarioGraph";
import { stickyNoteIconSrc } from "../components/toolbars/creator/ComponentIcon";

export const StickyNoteType = "StickyNoteNode";

export const StickyNoteDefinition: ComponentDefinition = {
    parameters: [],
    returnType: null,
    icon: stickyNoteIconSrc,
    docsUrl: null,
    outputParameters: null,
    label: "",
};
