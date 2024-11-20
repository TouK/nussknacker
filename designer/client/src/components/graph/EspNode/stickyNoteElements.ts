import { ProcessDefinitionData } from "../../../types";
import { Theme } from "@mui/material";
import { StickyNote } from "../../../common/StickyNote";
import { dia, elementTools, shapes } from "jointjs";
import { stickyNoteIcon } from "../../toolbars/creator/ComponentIcon";
import { createStickyNoteId } from "../../../types/stickyNote";
import { getStickyNoteBackgroundColor } from "../../../containers/theme/helpers";
import { CONTENT_PADDING, ICON_SIZE, MARKDOWN_EDITOR_NAME, STICKY_NOTE_CONSTRAINTS, StickyNoteShape } from "./stickyNote";
import { Events } from "../types";

export type ModelWithTool = {
    model: shapes.devs.Model;
    tools: dia.ToolsView;
};

export function makeStickyNoteElement(
    processDefinitionData: ProcessDefinitionData,
    theme: Theme,
): (stickyNote: StickyNote) => ModelWithTool {
    return (stickyNote: StickyNote) => {
        const attributes: shapes.devs.ModelAttributes = {
            id: createStickyNoteId(stickyNote.noteId),
            noteId: stickyNote.noteId,
            attrs: {
                size: {
                    width: stickyNote.dimensions.width,
                    height: stickyNote.dimensions.height,
                },
                body: {
                    fill: getStickyNoteBackgroundColor(theme, stickyNote.color).main,
                    opacity: 1,
                },
                foreignObject: {
                    width: stickyNote.dimensions.width,
                    height: stickyNote.dimensions.height - ICON_SIZE - CONTENT_PADDING * 4,
                    color: theme.palette.getContrastText(getStickyNoteBackgroundColor(theme, stickyNote.color).main),
                },
                icon: {
                    xlinkHref: stickyNoteIcon,
                    opacity: 1,
                    color: theme.palette.getContrastText(getStickyNoteBackgroundColor(theme, stickyNote.color).main),
                },
                border: {
                    stroke: getStickyNoteBackgroundColor(theme, stickyNote.color).dark,
                    strokeWidth: 1,
                },
            },
            rankDir: "R",
        };

        const ThemedStickyNoteShape = StickyNoteShape(theme, stickyNote);
        const stickyNoteModel = new ThemedStickyNoteShape(attributes);

        const removeButtonTool = new elementTools.Remove({
            focusOpacity: 0.5,
            rotate: true,
            x: stickyNote.dimensions.width - 20,
            y: "0%",
            offset: { x: 10, y: 10 },
            action: function () {
                stickyNoteModel.trigger(Events.CELL_DELETED, stickyNoteModel);
            },
        });

        const ResizeTool = elementTools.Control.extend({
            children: [
                {
                    tagName: "path",
                    selector: "handle",
                    attributes: {
                        d: "M 4 0 L 4 4 L 0 4 L 0 5 L 5 5 L 5 0 L 4 0",
                        stroke: getStickyNoteBackgroundColor(theme, stickyNote.color).light,
                        cursor: "pointer",
                    },
                },
                {
                    tagName: "rect",
                    selector: "extras",
                    attributes: {
                        "pointer-events": "none",
                        fill: "none",
                        stroke: getStickyNoteBackgroundColor(theme, stickyNote.color).light,
                        "stroke-dasharray": "2,3",
                        rx: 6,
                        ry: 6,
                    },
                },
            ],
            documentEvents: {
                mousemove: "onPointerMove",
                touchmove: "onPointerMove",
                mouseup: "onPointerUpCustom",
                touchend: "onPointerUpCustom",
                touchcancel: "onPointerUp",
            },
            getPosition: function (view) {
                const model = view.model;
                const { width, height } = model.size();
                return { x: width, y: height };
            },
            setPosition: function (view, coordinates) {
                const model = view.model;
                model.resize(
                    Math.max(
                        Math.min(STICKY_NOTE_CONSTRAINTS.MAX_WIDTH, Math.round(coordinates.x - 10)),
                        STICKY_NOTE_CONSTRAINTS.MIN_WIDTH,
                    ),
                    Math.max(
                        Math.min(STICKY_NOTE_CONSTRAINTS.MAX_HEIGHT, Math.round(coordinates.y - 10)),
                        STICKY_NOTE_CONSTRAINTS.MIN_HEIGHT,
                    ),
                );
            },
            onPointerUpCustom: function (evt: dia.Event) {
                this.onPointerUp(evt);
                stickyNoteModel.trigger(Events.CELL_RESIZED, stickyNoteModel);
            },
        });

        const tools: dia.ToolsView = new dia.ToolsView({
            tools: [
                new ResizeTool({
                    selector: "body",
                    scale: 2,
                }),
                removeButtonTool,
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
