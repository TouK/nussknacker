import "ace-builds/src-noconflict/ace";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../../../reducers/selectors/settings";
import { getProcessToDisplay } from "../../../../../reducers/selectors/graph";
import { BackendExpressionSuggester } from "./ExpressionSuggester";
import HttpService from "../../../../../http/HttpService";
import { allValid, Validator } from "../Validators";
import AceEditor from "./AceWithSettings";
import ValidationLabels from "../../../../modals/ValidationLabels";
import ReactAce from "react-ace/lib/ace";
import { EditorMode, ExpressionLang } from "./types";
import { SerializedStyles } from "@emotion/react";
import { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import { cx } from "@emotion/css";

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
    validators: Validator[];
    validationLabelInfo: string;
    showValidation?: boolean;
    isMarked?: boolean;
    variableTypes: Record<string, unknown>;
    editorMode?: EditorMode;
}

function ExpressionSuggest(props: Props): JSX.Element {
    const { isMarked, showValidation, inputProps, validators, variableTypes, validationLabelInfo } = props;

    const definitionData = useSelector(getProcessDefinitionData);
    const dataResolved = !isEmpty(definitionData);
    const { processingType, id, properties } = useSelector(getProcessToDisplay);

    const { value, onValueChange, language } = inputProps;
    const [editorFocused, setEditorFocused] = useState(false);

    const expressionSuggester = useMemo(() => {
        return new BackendExpressionSuggester(language, variableTypes, id, properties, processingType, HttpService);
    }, [processingType, id, properties, variableTypes, language]);

    const [customAceEditorCompleter] = useState(() => new CustomAceEditorCompleter(expressionSuggester));
    useEffect(() => customAceEditorCompleter.replaceSuggester(expressionSuggester), [customAceEditorCompleter, expressionSuggester]);

    const onChange = useCallback((value: string) => onValueChange(value), [onValueChange]);
    const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), []);

    return dataResolved ? (
        <>
            <div
                className={cx([
                    "row-ace-editor",
                    showValidation && !allValid(validators, [value]) && "node-input-with-error",
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
            {showValidation && <ValidationLabels validators={validators} values={[value]} validationLabelInfo={validationLabelInfo} />}
        </>
    ) : null;
}

export default ExpressionSuggest;
