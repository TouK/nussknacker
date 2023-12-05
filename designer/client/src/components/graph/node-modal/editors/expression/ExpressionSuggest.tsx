import "ace-builds/src-noconflict/ace";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../../../reducers/selectors/settings";
import { getProcessToDisplay } from "../../../../../reducers/selectors/graph";
import { BackendExpressionSuggester } from "./ExpressionSuggester";
import HttpService from "../../../../../http/HttpService";
import ProcessUtils from "../../../../../common/ProcessUtils";
import ReactDOMServer from "react-dom/server";
import cn from "classnames";
import AceEditor from "./AceWithSettings";
import ValidationLabels from "../../../../modals/ValidationLabels";
import ReactAce from "react-ace/lib/ace";
import { EditorMode, ExpressionLang } from "./types";
import type { Ace } from "ace-builds";
import { NodeValidationError } from "../../../../../types";

const { TokenIterator } = ace.require("ace/token_iterator");

//to reconsider
// - respect categories for global variables?
// - maybe ESC should be allowed to hide suggestions but leave modal open?

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
import { SerializedStyles } from "@emotion/react";
import { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import { cx } from "@emotion/css";
import { VariableTypes } from "../../../../../types";

interface InputProps {
    value: string;
    language: ExpressionLang | string;
    readOnly?: boolean;
    rows?: number;
    onValueChange: (value: string) => void;
    ref: React.Ref<ReactAce>;
    className?: string;
    style: SerializedStyles;
    cols: number;
    editorMode?: EditorMode;
}

interface Props {
    inputProps: InputProps;
    fieldErrors: NodeValidationError[];
    validationLabelInfo: string;
    showValidation?: boolean;
    isMarked?: boolean;
    variableTypes: VariableTypes;
    editorMode?: EditorMode;
}

function ExpressionSuggest(props: Props): JSX.Element {
    const { isMarked, showValidation, inputProps, fieldErrors, variableTypes, validationLabelInfo } = props;

    const definitionData = useSelector(getProcessDefinitionData);
    const dataResolved = !isEmpty(definitionData);
    const { processingType } = useSelector(getProcessToDisplay);

    const { value, onValueChange, language } = inputProps;
    const [editorFocused, setEditorFocused] = useState(false);

    const expressionSuggester = useMemo(() => {
        return new BackendExpressionSuggester(language, variableTypes, processingType, HttpService);
    }, [processingType, variableTypes, language]);

    const [customAceEditorCompleter] = useState(() => new CustomAceEditorCompleter(expressionSuggester));
    useEffect(() => customAceEditorCompleter.replaceSuggester(expressionSuggester), [customAceEditorCompleter, expressionSuggester]);

    const onChange = useCallback((value: string) => onValueChange(value), [onValueChange]);
    const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), []);

    console.log("firldErrors", fieldErrors);
    return dataResolved ? (
        <>
            <div
                className={cx([
                    "row-ace-editor",
                    showValidation && !isEmpty(fieldErrors) && "node-input-with-error",
                    isMarked && "marked",
                    editorFocused && "focused",
                    inputProps.readOnly && "read-only",
                ])}
            >
                <AceEditor
                    ref={inputProps.ref}
                    value={value}
                    onChange={onChange}
                    onFocus={editorFocus(true)}
                    onBlur={editorFocus(false)}
                    inputProps={inputProps}
                    customAceEditorCompleter={customAceEditorCompleter}
                />
            </div>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} validationLabelInfo={validationLabelInfo} />}
        </>
    ) : null;
}

export default ExpressionSuggest;
