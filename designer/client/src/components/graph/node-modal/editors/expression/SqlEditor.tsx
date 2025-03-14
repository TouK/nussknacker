import { cx } from "@emotion/css";
import i18next from "i18next";
import { debounce, flatMap, uniq } from "lodash";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ReactAce from "react-ace/lib/ace";
import { ExtendedEditor } from "./Editor";
import { Formatter } from "./Formatter";
import { SpelEditor, SpelEditorProps } from "./SpelEditor";
import { isSwitchableTo } from "./StringEditor";
import { EditorMode } from "./types";
import { Ace } from "ace-builds";
import { editorsParameters } from "./editorsParameters";

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

export interface Props extends Omit<SpelEditorProps, "language"> {
    formatter: Formatter;
}

const CLASSNAME = "tokenizer-working";

function useAliasUsageHighlight(token = "alias") {
    const [keywords, setKeywords] = useState<string>("");
    const ref = useRef<ReactAce>();
    const editor = ref.current?.editor;
    const session = useMemo(() => editor?.getSession() as unknown as EditSession, [editor]);

    const getValuesForToken = useCallback(
        (line: string, index: number) =>
            session
                ?.getTokens(index)
                .filter(({ type }) => type === token)
                .map(({ value }) => value.trim().toLowerCase()),
        [session, token],
    );

    const toggleClassname = useMemo(
        () =>
            debounce(
                (classname: string, enabled: boolean): void => {
                    const el = ref.current?.refEditor;

                    if (el) {
                        if (!enabled) {
                            el.className = el.className.replace(classname, "");
                        }

                        if (!el.className.includes(classname)) {
                            el.className = cx(el.className, { [classname]: enabled });
                        }
                    }
                },
                1000,
                { trailing: true, leading: true },
            ),
        [],
    );

    useEffect(() => {
        if (session?.getMode().$highlightRules.setAliases) {
            // for cypress tests only, we need some "still working" state
            toggleClassname(CLASSNAME, true);
            session.bgTokenizer.stop();
            session.getMode().$highlightRules.setAliases(keywords);
            session.bgTokenizer.start(0);
        }
    }, [toggleClassname, session, keywords]);

    useEffect(() => {
        const callback = () => {
            const allLines = session.bgTokenizer.lines;
            const next = uniq(flatMap(allLines, getValuesForToken)).join("|");
            setKeywords(next);
            toggleClassname(CLASSNAME, false);
        };
        session?.on(`tokenizerUpdate`, callback);
        return () => {
            session?.off(`tokenizerUpdate`, callback);
        };
    }, [toggleClassname, session, getValuesForToken]);

    return ref;
}

export const SqlEditor: ExtendedEditor<Props> = (props: Props) => {
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
            language={editorsParameters.SqlParameterEditor.language}
        />
    );
};

SqlEditor.isSwitchableTo = isSwitchableTo;
SqlEditor.notSwitchableToHint = () =>
    i18next.t(
        "editors.textarea.notSwitchableToHint",
        "Expression must be a string literal i.e. text surrounded by quotation marks to switch to {{editorName}} mode",
        { editorName: editorsParameters.SqlParameterEditor.displayName },
    );
