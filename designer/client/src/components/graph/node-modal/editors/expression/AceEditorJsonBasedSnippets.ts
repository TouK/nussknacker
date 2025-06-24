import type { Ace } from "ace-builds";
import ace from "ace-builds/src-noconflict/ace";

interface EditorContext {
    hasEmptyObject: boolean;
    hasOpeningBraceOnly: boolean;
    isAfterComma: boolean;
}

export const setupAceEditorSnippets = (editor: Ace.Editor): void => {
    const editorMode = (editor.session.getMode() as any).$id;
    const isJsonEditor = editorMode.includes("json");
    const isJsonTemplateEditor = editorMode.includes("jsonTemplate");

    if (isJsonEditor || isJsonTemplateEditor) {
        setupJsonBasedSnippets(editor);
    }
};

const setupJsonBasedSnippets = (editor: Ace.Editor): void => {
    editor.commands.addCommand({
        name: "insertKeyValueOnEnter",
        bindKey: { win: "Enter", mac: "Enter" },
        exec: (editor: Ace.Editor): void => {
            const session = editor.session;
            const pos = editor.getCursorPosition();
            const line = session.getLine(pos.row);
            const indent = session.getTabString();
            const currentIndent = line.match(/^\s*/)[0];

            // Extract cursor context
            const beforeCursor = line.substring(0, pos.column);
            const afterCursor = line.substring(pos.column);

            // Determine editor context for snippet insertion
            const context = getEditorContext(session, pos, beforeCursor, afterCursor);

            // Handle different contexts with appropriate snippets
            handleSnippetInsertion(editor, context, currentIndent, indent);
        },
    });
};

// Context detection - determine where the cursor is positioned
const getEditorContext = (session: Ace.EditSession, pos: Ace.Position, beforeCursor: string, afterCursor: string): EditorContext => {
    // Empty object on same line: {} or { }
    const hasEmptyObjectOnSameLine =
        (beforeCursor.trimRight().endsWith("{") && afterCursor.trimLeft().startsWith("}")) ||
        (beforeCursor.endsWith("{") && afterCursor.startsWith("}"));

    // Find first non-whitespace character before cursor
    let previousFirstChar = "";
    const beforeCursorTrimmed = beforeCursor.trim();
    if (beforeCursorTrimmed.length > 0) {
        // First check current line before cursor
        previousFirstChar = beforeCursorTrimmed.charAt(beforeCursorTrimmed.length - 1);
    } else {
        // Then check previous lines
        for (let row = pos.row - 1; row >= 0; row--) {
            const line = session.getLine(row).trim();
            if (line.length > 0) {
                previousFirstChar = line.charAt(line.length - 1);
                break;
            }
        }
    }

    // Find first non-whitespace character after cursor
    let nextFirstChar = "";
    const afterCursorTrimmed = afterCursor.trim();
    if (afterCursorTrimmed.length > 0) {
        // First check current line after cursor
        nextFirstChar = afterCursorTrimmed.charAt(0);
    } else {
        // Then check next lines
        for (let row = pos.row + 1; row < session.getLength(); row++) {
            const line = session.getLine(row).trim();
            if (line.length > 0) {
                nextFirstChar = line.charAt(0);
                break;
            }
        }
    }

    const hasEmptyObjectAcrossLines = previousFirstChar === "{" && nextFirstChar === "}";

    // Just an opening brace (need to add closing brace)
    const hasOpeningBraceOnly = beforeCursor.trimRight().endsWith("{") && !(nextFirstChar === "}" || afterCursor.includes("}"));

    // Between properties (line ends with comma)
    const isAfterComma = session.getLine(pos.row).trim().endsWith(",");

    return {
        hasEmptyObject: hasEmptyObjectOnSameLine || hasEmptyObjectAcrossLines,
        hasOpeningBraceOnly,
        isAfterComma,
    };
};

// Handle snippet insertion based on context
const handleSnippetInsertion = (editor: Ace.Editor, context: EditorContext, currentIndent: string, indent: string): void => {
    const snippetManager = ace.require("ace/snippets").snippetManager;
    const snippetTemplate = '"${1:}": ${2:}';

    if (context.hasEmptyObject || context.hasOpeningBraceOnly) {
        // For empty object or lone opening brace
        let snippet = `\n${currentIndent}${indent}${snippetTemplate}\n${currentIndent}`;

        // Add closing brace if we only have an opening brace
        if (context.hasOpeningBraceOnly) {
            snippet += "}";
        }

        snippetManager.insertSnippet(editor, snippet);
    } else if (context.isAfterComma) {
        // For properties after comma
        snippetManager.insertSnippet(editor, `\n${snippetTemplate}`);
    } else {
        // Default Enter behavior
        editor.execCommand("insertstring", "\n");
    }
};
