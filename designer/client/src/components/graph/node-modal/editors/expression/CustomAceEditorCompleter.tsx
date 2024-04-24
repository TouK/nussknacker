import { isEmpty, overSome } from "lodash";
import React from "react";
import { ExpressionSuggester, ExpressionSuggestion } from "./ExpressionSuggester";
import ProcessUtils from "../../../../../common/ProcessUtils";
import ReactDOMServer from "react-dom/server";
import type { Ace } from "ace-builds";
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

interface Editor extends Ace.Editor {
    readonly completer: {
        activated: boolean;
        openPopup: (editor: Editor, prefix: string, keepPopupPosition: boolean) => void;
    };
}

interface EditSession extends Ace.EditSession {
    readonly $modeId: unknown;
}

interface Completion extends Ace.ValueCompletion {
    readonly className: `${string} ace_`; //not documented, hack with some private api
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

const suggestionToCompletion = (suggestion: ExpressionSuggestion): Completion => ({
    value: suggestion.methodName,
    score: suggestion.fromClass ? 1 : 1000,
    meta: ProcessUtils.humanReadableType(suggestion.refClazz),
    docHTML: ReactDOMServer.renderToStaticMarkup(<DocHTML {...suggestion} />),
    className: `${suggestion.fromClass ? `class` : `default`}Method ace_`, //not documented, some private api
});

export class CustomAceEditorCompleter implements Ace.Completer {
    private isTokenAllowed = overSome([isSqlTokenAllowed, isSpelTokenAllowed]);
    // We add hash to identifier pattern to start suggestions just after hash is typed
    public identifierRegexps = identifierRegexpsWithoutDot;
    // This is necessary to make live auto complete works after dot
    public triggerCharacters = ["."];
    private revertOpenPopup: (() => void) | null;

    constructor(private expressionSuggester: ExpressionSuggester) {}

    replaceSuggester(expressionSuggester: ExpressionSuggester) {
        this.expressionSuggester = expressionSuggester;
    }

    getCompletions(
        editor: Editor,
        session: EditSession,
        caretPosition2d: Ace.Point,
        prefix: string,
        callback: Ace.CompleterCallback,
    ): void {
        const iterator = new TokenIterator(session, caretPosition2d.row, caretPosition2d.column);
        if (!this.isTokenAllowed(iterator, session.$modeId)) {
            callback(null, []);
        }

        const value = editor.getValue();

        this.overrideOpenPopup(editor);

        this.expressionSuggester.suggestionsFor(value, caretPosition2d).then((suggestions) => {
            callback(null, suggestions.map(suggestionToCompletion));
        });
    }

    private overrideOpenPopup({ completer }: Editor) {
        if (!this.revertOpenPopup) {
            const originalFn = completer.openPopup;

            completer.openPopup = function (editor, prefix, keepPopupPosition) {
                // prevent popup detach when fast switching from fully fitted completion to dotted completions
                // this occurs when prefix is empty after entering "." at the end
                // we could safely force some not empty prefix - backend reads whole line
                const modifiedPrefix = keepPopupPosition && !prefix ? "." : prefix;
                originalFn.apply(this, [editor, modifiedPrefix, keepPopupPosition]);
            };

            this.revertOpenPopup = () => {
                completer.openPopup = originalFn;
                this.revertOpenPopup = null;
            };
        }
    }

    cancel = () => this.revertOpenPopup();
}
