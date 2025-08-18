import type { Theme } from "@mui/material";
import type { shapes } from "jointjs";
import { dia, elementTools } from "jointjs";

import type { StickyNoteNodeType } from "../../../types";
import { MARKDOWN_EDITOR_NAME, STICKY_NOTE_CONSTRAINTS, StickyNoteShape } from "./stickyNote";
import { stickyNoteAdvancedAttributes, stickyNoteAdvancedResizeTool } from "./stickyNote/advancedStickyNoteConfig";
import { stickyNoteBasicAttributes, stickyNoteBasicResizeTool } from "./stickyNote/basicStickyNoteConfig";

export type ModelWithTool = {
    model: shapes.devs.Model;
    tools: dia.ToolsView;
};

export function makeStickyNoteElement(
    theme: Theme,
    areAdvancedStickyNotesEnabled: boolean,
): (stickyNote: StickyNoteNodeType) => ModelWithTool {
    return (stickyNote: StickyNoteNodeType) => {
        const attributes: shapes.devs.ModelAttributes = areAdvancedStickyNotesEnabled
            ? stickyNoteAdvancedAttributes(stickyNote, theme)
            : stickyNoteBasicAttributes(stickyNote, theme);

        const ThemedStickyNoteShape = StickyNoteShape(theme, stickyNote, areAdvancedStickyNotesEnabled);
        const stickyNoteModel = new ThemedStickyNoteShape(attributes);

        const ResizeTool = elementTools.Control.extend(
            areAdvancedStickyNotesEnabled
                ? stickyNoteAdvancedResizeTool(stickyNote, theme, stickyNoteModel)
                : stickyNoteBasicResizeTool(stickyNote, theme, stickyNoteModel),
        );

        const tools: dia.ToolsView = new dia.ToolsView({
            tools: [
                new ResizeTool({
                    selector: "body",
                    scale: 2,
                }),
            ],
        });
        stickyNoteModel.resize(
            Math.max(stickyNote.dimensions.width, STICKY_NOTE_CONSTRAINTS.MIN_WIDTH),
            Math.max(stickyNote.dimensions.height, STICKY_NOTE_CONSTRAINTS.MIN_HEIGHT),
        );
        stickyNoteModel.attr(`${MARKDOWN_EDITOR_NAME}/props/value`, stickyNote.content);
        return { model: stickyNoteModel, tools };
    };
}
