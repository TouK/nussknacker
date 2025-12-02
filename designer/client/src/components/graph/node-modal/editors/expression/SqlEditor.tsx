import type { Ace } from "ace-builds";
import i18next from "i18next";
import React, { useCallback, useLayoutEffect, useRef } from "react";
import type ReactAce from "react-ace/lib/ace";

import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { isQuoted } from "./SpelQuotesUtils";
import type { ExpressionObj } from "./types";
import { EditorMode, EditorType, ExpressionLang } from "./types";

interface SyntaxMode extends Ace.SyntaxMode {
    $highlightRules: {
        setAliases(string): void;
    };
}

interface EditSession extends Ace.EditSession {
    bgTokenizer: {
        lines: string[];
        stop(): void;
        start(integer): void;
    };

    getMode(): SyntaxMode;
}

export type Props = Omit<SpelEditorProps, "language">;

function useAliasUsageHighlight(token = "alias") {
    const ref = useRef<ReactAce>(null);

    const toggleTokenizerWorkingClass = useCallback((value: boolean) => {
        ref.current?.refEditor?.classList.toggle("tokenizer-working", value);
    }, []);

    const getNextKeywords = useCallback(() => {
        const keywordsSet = new Set<string>();
        const session: EditSession = ref.current?.editor?.getSession() as any;
        session?.bgTokenizer.lines.forEach((line, index) => {
            session.getTokens(index).forEach(({ value, type }) => {
                if (type === token) {
                    keywordsSet.add(value.trim().toLowerCase());
                }
            });
        });
        return [...keywordsSet].join("|");
    }, [token]);

    useLayoutEffect(() => {
        const session: EditSession = ref.current?.editor?.getSession() as any;
        if (!session?.getMode().$highlightRules?.setAliases) return;

        let prevKeywords = "";
        const tokenizerUpdate = () => {
            const nextKeywords = getNextKeywords();
            if (prevKeywords === nextKeywords) {
                toggleTokenizerWorkingClass(false);
            } else {
                toggleTokenizerWorkingClass(true);
                session.bgTokenizer.stop();
                prevKeywords = nextKeywords;
                session.getMode().$highlightRules.setAliases(nextKeywords);
                session.bgTokenizer.start(0);
            }
        };

        session.on(`tokenizerUpdate`, tokenizerUpdate);

        toggleTokenizerWorkingClass(true);
        tokenizerUpdate();

        return () => {
            session.off(`tokenizerUpdate`, tokenizerUpdate);
            toggleTokenizerWorkingClass(false);
        };
    }, [getNextKeywords, toggleTokenizerWorkingClass]);

    return ref;
}

const splitConcats = (value: string) => {
    return value.split(/\s*\+\s*/gm);
};

export const isSwitchableTo = ({ expression, language }: ExpressionObj): boolean => {
    return language === ExpressionLang.SpEL && splitConcats(expression.trim()).some(isQuoted);
};

export const SqlEditor = prepareEditor<Props>(
    (props) => {
        const { expressionObj, onValueChange, className, ...passProps } = props;
        const ref = useAliasUsageHighlight();

        return (
            <SpelEditor
                {...passProps}
                ref={ref}
                onValueChange={onValueChange}
                expressionObj={expressionObj}
                className={className}
                rows={6}
                editorMode={EditorMode.SQL}
                language={editorsParameters[EditorType.SQL_PARAMETER_EDITOR].language}
            />
        );
    },
    {
        isSwitchableTo,
        notSwitchableToHint: () =>
            i18next.t(
                "editors.textarea.notSwitchableToHint",
                "Expression must be a string literal i.e. text surrounded by quotation marks to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.SQL_PARAMETER_EDITOR].displayName },
            ),
    },
);
