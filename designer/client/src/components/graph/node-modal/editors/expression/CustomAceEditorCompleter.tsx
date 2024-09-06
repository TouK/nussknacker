import { isEmpty, overSome } from "lodash";
import React from "react";
import { ExpressionSuggester, ExpressionSuggestion } from "./ExpressionSuggester";
import ProcessUtils from "../../../../../common/ProcessUtils";
import ReactDOMServer from "react-dom/server";
import { Ace } from "ace-builds";
import ace from "ace-builds/src-noconflict/ace";
import { Divider } from "@mui/material";

const { TokenIterator } = ace.require("ace/token_iterator");

//to reconsider
// - respect categories for global variables?

const identifierRegexpsWithoutDot = [/[#a-zA-Z0-9-_]/];

function isSqlTokenAllowed(iterator, modeId): boolean {
    if (modeId === "ace/mode/sql") {
        let token = iterator.getCurrentToken();
        while (token && token.type !== "spel.start" && token.type !== "spel.end") {
            token = iterator.stepBackward();
        }
        return token?.type === "spel.start";
    }
    return false;
}

function isSpelTokenAllowed(iterator, modeId): boolean {
    // We need to handle #dict['Label'], where Label is a string token
    return modeId === "ace/mode/spel" || modeId === "ace/mode/spelTemplate";
}

declare module "ace-builds" {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Ace {
        interface Autocomplete {
            base: Ace.Point;
            editor: Ace.Editor;
            activated: boolean;
            openPopup: (this: Autocomplete, editor: Ace.Editor, prefix: string, keepPopupPosition: boolean) => void;
            updateCompletions: (this: Autocomplete, keepPopupPosition: boolean, options: Ace.CompletionOptions) => void;
        }

        interface Editor {
            readonly completer: Autocomplete;
        }
    }
}

interface EditSession extends Ace.EditSession {
    readonly $modeId: unknown;
}

interface Completion extends Ace.ValueCompletion {
    readonly className: `${string} ace_`; //not documented, hack with some private api
    readonly completer: Ace.Completer;
}

const DocHTML = ({ description, parameters, methodName, refClazz }: ExpressionSuggestion) => {
    if (!(description || !isEmpty(parameters))) {
        return null;
    }

    const paramsSignature = parameters.map((p) => `${ProcessUtils.humanReadableType(p.refClazz)} ${p.name}`).join(", ");
    return (
        <div className="function-docs">
            <b>
                {ProcessUtils.humanReadableType(refClazz)} {methodName}({paramsSignature})
            </b>
            <Divider />
            <p>{description}</p>
        </div>
    );
};

const getNormalizedValue = (suggestion: ExpressionSuggestion): string => {
    switch (suggestion.refClazz.type) {
        case "TypedDict":
            if (suggestion.methodName.match(/\s/)) {
                return `["${suggestion.methodName}"]`;
            }
    }
    if (suggestion.parameters?.length > 0) {
        return `${suggestion.methodName}()`;
    }
    return suggestion.methodName;
};

const suggestionToCompletion = (completer: Ace.Completer, suggestion: ExpressionSuggestion): Completion => ({
    value: getNormalizedValue(suggestion),
    caption: suggestion.methodName,
    score: suggestion.fromClass ? 1 : 1000,
    meta: ProcessUtils.humanReadableType(suggestion.refClazz),
    docHTML: ReactDOMServer.renderToStaticMarkup(<DocHTML {...suggestion} />),
    className: `${suggestion.fromClass ? `class` : `default`}Method ace_`, //not documented, some private api
    completer,
});

export class CustomAceEditorCompleter implements Ace.Completer {
    private isTokenAllowed = overSome([isSqlTokenAllowed, isSpelTokenAllowed]);
    // We add hash to identifier pattern to start suggestions just after hash is typed
    public identifierRegexps = identifierRegexpsWithoutDot;
    // This is necessary to make live auto complete works after dot
    public triggerCharacters = ["."];
    private revertCompleterOverrides: (() => void) | null;

    constructor(private expressionSuggester: ExpressionSuggester) {}

    onInsert = (editor: Ace.Editor, { value }: Completion) => {
        // correct wrong dict value after insert by removing dot.
        if (value.match(/^\[".*"]$/)) {
            editor.session.replace(editor.find(`.${value}`, { backwards: true }), value);
        }
    };

    replaceSuggester(expressionSuggester: ExpressionSuggester) {
        this.expressionSuggester = expressionSuggester;
    }

    getCompletions(
        editor: Ace.Editor,
        session: EditSession,
        caretPosition2d: Ace.Point,
        prefix: string,
        callback: Ace.CompleterCallback,
    ): void {
        this.overrideCompleter(editor);

        const iterator = new TokenIterator(session, caretPosition2d.row, caretPosition2d.column);
        if (!this.isTokenAllowed(iterator, session.$modeId)) {
            return callback(null, []);
        }

        const value = editor.getValue();

        this.expressionSuggester.suggestionsFor(value, caretPosition2d).then((suggestions) => {
            callback(
                null,
                suggestions.map((s) => suggestionToCompletion(this, s)),
            );
        });
    }

    private overrideCompleter({ completer }: Ace.Editor) {
        if (!this.revertCompleterOverrides) {
            const original_updateCompletions = completer.updateCompletions;
            const triggerCharacters = this.triggerCharacters;

            completer.updateCompletions = function (keepPopupPosition, options) {
                const cursorPosition = this.editor.getCursorPosition();
                const range = { start: this.base, end: cursorPosition } as Ace.Range;
                const prefix = this.editor.session.getTextRange(range);

                keepPopupPosition = triggerCharacters.some((trigger) => prefix.endsWith(trigger)) ? false : keepPopupPosition;

                return original_updateCompletions.apply(this, [keepPopupPosition, options]);
            };

            this.revertCompleterOverrides = () => {
                completer.updateCompletions = original_updateCompletions;
                this.revertCompleterOverrides = null;
            };
        }
    }

    cancel = () => {
        this.revertCompleterOverrides();
        this.expressionSuggester.cancel();
    };
}
