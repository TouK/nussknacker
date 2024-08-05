import "ace-builds/src-noconflict/ace";
import { isEmpty, isEqual } from "lodash";
import React, { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../../../reducers/selectors/settings";
import { getProcessingType } from "../../../../../reducers/selectors/graph";
import { BackendExpressionSuggester } from "./ExpressionSuggester";
import HttpService from "../../../../../http/HttpService";
import { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import { VariableTypes } from "../../../../../types";
import { CustomCompleterAceEditor, CustomCompleterAceEditorProps } from "./CustomCompleterAceEditor";

export type ExpressionSuggestProps = Omit<CustomCompleterAceEditorProps, "completer" | "isLoading"> & {
    variableTypes: VariableTypes;
};

function useDeepMemo<T>(factory: () => T, deps: React.DependencyList): T {
    const ref = useRef<{ value: T; deps: React.DependencyList }>();

    if (!ref.current || !isEqual(deps, ref.current.deps)) {
        ref.current = { value: factory(), deps };
    }

    return ref.current.value;
}

export function ExpressionSuggest(props: ExpressionSuggestProps): JSX.Element {
    const { className, isMarked, showValidation, inputProps, fieldErrors, variableTypes, validationLabelInfo } = props;

    const definitionData = useSelector(getProcessDefinitionData);
    const dataResolved = !isEmpty(definitionData);
    const processingType = useSelector(getProcessingType);

    const { language } = inputProps;

    const expressionSuggester = useDeepMemo(
        () => new BackendExpressionSuggester(language, variableTypes, processingType, HttpService),
        [language, variableTypes, processingType],
    );

    const [isLoading, setIsLoading] = useState(false);
    useEffect(() => expressionSuggester.on("stateChange", setIsLoading), [expressionSuggester]);

    const [customAceEditorCompleter] = useState(() => new CustomAceEditorCompleter(expressionSuggester));
    useEffect(() => customAceEditorCompleter.replaceSuggester(expressionSuggester), [customAceEditorCompleter, expressionSuggester]);

    return dataResolved ? (
        <CustomCompleterAceEditor
            className={className}
            isMarked={isMarked}
            showValidation={showValidation}
            inputProps={inputProps}
            fieldErrors={fieldErrors}
            validationLabelInfo={validationLabelInfo}
            completer={customAceEditorCompleter}
            isLoading={isLoading}
        />
    ) : null;
}
