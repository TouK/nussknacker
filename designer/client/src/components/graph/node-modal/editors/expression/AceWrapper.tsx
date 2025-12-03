/* eslint-disable i18next/no-literal-string */
import type { SerializedStyles } from "@emotion/react";
import { Box } from "@mui/material";
import type { Ace } from "ace-builds";
import { trimStart } from "lodash";
import type { ForwardedRef, ReactNode } from "react";
import React, { forwardRef } from "react";
import type ReactAce from "react-ace/lib/ace";
import type { IAceEditorProps } from "react-ace/lib/ace";
import type { ICommand } from "react-ace/lib/types";
import type { IAceOptions, IEditorProps } from "react-ace/src/types";

import type { FieldError } from "../Validators";
import { AceEditor } from "./ace";
import type { EditorMode } from "./types";
import { ExpressionLang } from "./types";

export type AceWrapperInputProps = {
    language: ExpressionLang;
    readOnly?: boolean;
    editorMode?: EditorMode;
    className?: string;
    style?: SerializedStyles;
    rows?: number;
    cols?: number;
    placeholder?: string;
    InputAdornmentEnd?: ReactNode;
    useAceWorker?: boolean;
};

export interface AceWrapperProps
    extends Pick<IAceEditorProps, "value" | "onChange" | "onFocus" | "onBlur" | "wrapEnabled" | "annotations" | "markers" | "onLoad"> {
    inputProps: AceWrapperInputProps;
    customAceEditorCompleter?;
    showLineNumbers?: boolean;
    commands?: AceKeyCommand[];
    enableLiveAutocompletion?: boolean;
    fieldErrors: FieldError[];
}

export const DEFAULT_OPTIONS: IAceOptions = {
    indentedSoftWrap: false, //removes weird spaces for multiline strings when wrapEnabled=true
    enableSnippets: false,
    fontSize: 14,
    tabSize: 2,
    highlightActiveLine: false,
    highlightGutterLine: true,
};

const DEFAULT_EDITOR_PROPS: IEditorProps = {
    $blockScrolling: true,
};

export interface AceKeyCommand extends Omit<ICommand, "exec"> {
    readonly?: boolean;
    exec: (editor: Ace.Editor) => boolean | void;
}

function splitElements(sortedElements: HTMLElement[], currentElement: HTMLElement): [HTMLElement[], HTMLElement[]] {
    const index = sortedElements.indexOf(currentElement);
    const nextElements = sortedElements.slice(index + 1);
    const prevElements = sortedElements.slice(0, index);
    return [nextElements, prevElements];
}

export function getTabindexedElements(root: Element, currentElement?: HTMLElement): [HTMLElement[], HTMLElement[]] {
    const treeWalker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, (node: HTMLElement) => {
        if (currentElement === node) {
            return NodeFilter.FILTER_ACCEPT;
        }
        if (currentElement === node.parentElement) {
            return NodeFilter.FILTER_REJECT;
        }
        if (node.tabIndex < Math.max(0, currentElement?.tabIndex)) {
            return NodeFilter.FILTER_SKIP;
        }
        const rect = node.getBoundingClientRect();
        if (!rect.width || !rect.height) {
            return NodeFilter.FILTER_SKIP;
        }
        return NodeFilter.FILTER_ACCEPT;
    });

    const elements: HTMLElement[] = [];
    let node;
    while ((node = treeWalker.nextNode())) {
        elements.push(node as HTMLElement);
    }

    const sortedElements = elements.sort((a, b) => Math.max(0, a.tabIndex) - Math.max(0, b.tabIndex));

    if (sortedElements.length <= 1 && root !== document.body) {
        return getTabindexedElements(root.parentElement, currentElement);
    }

    if (!currentElement) {
        return [sortedElements, []];
    }

    const [nextElements, prevElements] = splitElements(sortedElements, currentElement);

    if ((prevElements.length <= 0 || nextElements.length <= 0) && root !== document.body) {
        return getTabindexedElements(root.parentElement, currentElement);
    }

    return [nextElements, prevElements];
}

function handleTab(editor: Ace.Editor, shiftKey?: boolean): boolean {
    const session = editor.getSession();
    if (session.getDocument().getAllLines().length > 1) {
        const selection = editor.getSelection();

        // allow indent multiple lines
        if (selection.isMultiLine() || selection.getAllRanges().length > 1) {
            return false;
        }

        const { row, column } = selection.getCursor();
        const line = session.getLine(row).slice(0, column);
        const trimmed = trimStart(line).length;
        // check if cursor is within whitespace starting part of line
        // always allow indent decrease
        if (!trimmed || (shiftKey && trimmed < line.length)) {
            return false;
        }
    }

    editor.blur();

    const [nextElements, prevElements] = getTabindexedElements(editor.container.parentElement.offsetParent, editor.container);
    const element = shiftKey ? prevElements.pop() : nextElements.shift();
    element?.focus();
}

export const DEFAULT_COMMANDS: AceKeyCommand[] = [
    {
        name: "find",
        bindKey: {
            win: "Ctrl-F",
            mac: "Command-F",
        },
        exec: () => false,
    },
    {
        name: "focusNext",
        bindKey: {
            win: "Tab",
            mac: "Tab",
        },
        exec: (editor) => handleTab(editor),
    },
    {
        name: "focusPrevious",
        bindKey: {
            win: "Shift-Tab",
            mac: "Shift-Tab",
        },
        exec: (editor) => handleTab(editor, true),
    },
];

function editorLangToMode(language: ExpressionLang | string, editorMode?: EditorMode): string {
    if (editorMode) {
        return editorMode.valueOf();
    }
    if (language === ExpressionLang.TabularDataDefinition) {
        return ExpressionLang.JSON;
    }
    return language;
}

export default forwardRef(function AceWrapper(
    {
        inputProps,
        customAceEditorCompleter,
        showLineNumbers,
        wrapEnabled = true,
        commands = [],
        enableLiveAutocompletion = true,
        annotations = [],
        markers = [],
        ...props
    }: AceWrapperProps,
    ref: ForwardedRef<ReactAce>,
): React.JSX.Element {
    const { language, readOnly, rows = 1, editorMode, InputAdornmentEnd, useAceWorker } = inputProps;

    return (
        <>
            <AceEditor
                {...props}
                ref={ref}
                mode={editorLangToMode(language, editorMode)}
                width={"100%"}
                minLines={rows}
                maxLines={512}
                theme={"nussknacker"}
                showPrintMargin={false}
                cursorStart={-1} //line start
                readOnly={readOnly}
                className={readOnly ? " read-only" : ""}
                wrapEnabled={!!wrapEnabled}
                showGutter={!!showLineNumbers}
                editorProps={DEFAULT_EDITOR_PROPS}
                setOptions={{
                    ...DEFAULT_OPTIONS,
                    enableLiveAutocompletion,
                    showLineNumbers,
                    // We don't want to check syntax correctness with ace
                    useWorker: useAceWorker,
                }}
                enableBasicAutocompletion={customAceEditorCompleter && [customAceEditorCompleter]}
                commands={[...DEFAULT_COMMANDS, ...commands] as unknown as ICommand[]}
                placeholder={inputProps.placeholder}
                annotations={annotations}
                markers={markers}
            />
            {InputAdornmentEnd && (
                <Box
                    aria-label={"InputAdornmentEnd"}
                    sx={{
                        position: "absolute",
                        right: "8px",
                        top: "9px",
                    }}
                >
                    {InputAdornmentEnd}
                </Box>
            )}
        </>
    );
});
