import type { Theme } from "@mui/material";
import DOMPurify from "dompurify";
import { dia } from "jointjs";
import { shapes, util } from "jointjs";
import { marked } from "marked";

import type { NodeValidationError, StickyNoteNodeType } from "../../../types";
import { StickyNoteElement } from "../StickyNoteElement";
import { stickyNoteAdvancedBorder, stickyNoteAdvancedDefaultDeps } from "./stickyNote/advancedStickyNoteConfig";
import { stickyNoteBasicBorder, stickyNoteBasicIcon, stickyNoteBasicDefaultDeps } from "./stickyNote/basicStickyNoteConfig";

import MarkupNodeJSON = dia.MarkupNodeJSON;

export const STICKY_NOTE_CONSTRAINTS = {
    MIN_WIDTH: 100,
    MAX_WIDTH: 3000,
    MIN_HEIGHT: 100,
    MAX_HEIGHT: 3000,
    CONTENT_PADDING: 5,
} as const;

export const MARKDOWN_EDITOR_NAME = "markdown-editor";

const body: dia.MarkupNodeJSON = {
    selector: "body",
    tagName: "path",
};

const renderer = new marked.Renderer();
renderer.link = function (href, title, text) {
    return `<a target="_blank" rel="noopener noreferrer" href="${href}">${text}</a>`;
};

renderer.hr = function () {
    return `----`; // SVG doesn't support HTML hr inside foreignObject
};

renderer.image = function (href, title, text) {
    // SVG doesn't support HTML img inside foreignObject
    return `<a target="_blank" rel="noopener noreferrer" href="${href}">${text} (attached img)</a>`;
};

const invalidMarkupError: NodeValidationError = {
    description: "Could not parse markdown content",
    details: null,
    errorType: "SaveNotAllowed",
    fieldName: null,
    message: "Could not parse markdown content",
    typ: "",
};

const prepareSvgObject = (content: string, errors?: NodeValidationError[]) =>
    util.svg/* xml */ `
            <foreignObject @selector="foreignObject">
                <div @selector="sticky-note-content" class="sticky-note-content">
                    ${
                        errors
                            ? `<div class="sticky-note-errors">${errors
                                  .map((e) => {
                                      return `<div class="sticky-note-error">${e.description}</div> `;
                                  })
                                  .join(" ")}</div>`
                            : ""
                    }
                    <textarea @selector="${MARKDOWN_EDITOR_NAME}" class="sticky-note-markdown-editor" name="${MARKDOWN_EDITOR_NAME}" autocomplete="off" disabled="disabled"></textarea>
                    <div @selector="markdown" class="sticky-note-markdown">${content}</div>
                </div>
            </foreignObject>
    `[0] as MarkupNodeJSON;

const escapeHtmlContent = (content: string) =>
    content.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");

const foreignObject = (stickyNote: StickyNoteNodeType): MarkupNodeJSON => {
    try {
        const contentWithHtmlTagsSanitized = escapeHtmlContent(stickyNote.content);
        let parsed = DOMPurify.sanitize(marked.parse(contentWithHtmlTagsSanitized, { renderer }), { ADD_ATTR: ["target"] });
        parsed = parsed.replace(/<br\s*\/?>/g, "<br/>"); // SVG does not allow tag without closing and DOMPurify always remove closing tag.
        return prepareSvgObject(parsed, stickyNote.errors);
    } catch (error) {
        console.error("Error: Could not parse markdown:", error);
        return prepareSvgObject(escapeHtmlContent(stickyNote.content), [...stickyNote.errors, invalidMarkupError]);
    }
};

export const stickyNotePath = "M 0 0 L 19 0 L 19 19 L 0 19 L 0 0";

const defaults = (theme: Theme, areAdvancedStickyNotesEnabled: boolean) =>
    util.defaultsDeep(
        areAdvancedStickyNotesEnabled ? stickyNoteAdvancedDefaultDeps(theme) : stickyNoteBasicDefaultDeps(theme),
        shapes.devs.Model.prototype.defaults,
    );

const protoProps = (stickyNote: StickyNoteNodeType, areAdvancedStickyNotesEnabled: boolean) => {
    const markup = [body, areAdvancedStickyNotesEnabled ? stickyNoteAdvancedBorder : stickyNoteBasicBorder, foreignObject(stickyNote)];

    if (!areAdvancedStickyNotesEnabled) {
        markup.push(stickyNoteBasicIcon);
    }

    return {
        markup,
    };
};

export const StickyNoteShape = (theme: Theme, stickyNote: StickyNoteNodeType, areAdvancedStickyNotesEnabled: boolean) =>
    StickyNoteElement(
        defaults(theme, areAdvancedStickyNotesEnabled),
        protoProps(stickyNote, areAdvancedStickyNotesEnabled),
    ) as typeof shapes.devs.Model;
