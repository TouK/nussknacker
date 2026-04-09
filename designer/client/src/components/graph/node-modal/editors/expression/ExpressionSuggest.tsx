import "ace-builds/src-noconflict/ace";
import { isEmpty, isEqual } from "lodash";
import React, { useEffect, useRef, useState } from "react";

import { createNodeNameByContextKey } from "../../../../../common/sanitizeBranchName";
import HttpService from "../../../../../http/HttpService/instance";
import { getProcessDefinitionData } from "../../../../../reducers/selectors/getProcessDefinitionData";
import { getProcessingType, getScenarioGraph } from "../../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { VariableTypes } from "../../../../../types/validation";
import { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import type { CustomCompleterAceEditorProps } from "./CustomCompleterAceEditor";
import { CustomCompleterAceEditor } from "./CustomCompleterAceEditor";
import { BackendExpressionSuggester } from "./ExpressionSuggester";

export type ExpressionSuggestProps = Omit<CustomCompleterAceEditorProps, "completer" | "isLoading"> & {
    variableTypes: VariableTypes;
};

function useDeepMemo<T>(factory: () => T, deps: React.DependencyList): T {
    const ref = useRef<{
        value: T;
        deps: React.DependencyList;
    }>(null);

    if (!ref.current || !isEqual(deps, ref.current.deps)) {
        ref.current = {
            value: factory(),
            deps,
        };
    }

    return ref.current.value;
}

export function ExpressionSuggest(props: ExpressionSuggestProps): React.JSX.Element {
    const { className, isMarked, showValidation, inputProps, fieldErrors, variableTypes, validationLabelInfo } = props;

    const definitionData = useAppSelector(getProcessDefinitionData);
    const dataResolved = !isEmpty(definitionData);
    const processingType = useAppSelector(getProcessingType);
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const nodeNameByContextKey = useDeepMemo(() => createNodeNameByContextKey(scenarioGraph.nodes || []), [scenarioGraph.nodes]);

    const { language } = inputProps;

    const expressionSuggester = useDeepMemo(
        () => new BackendExpressionSuggester(language, variableTypes, processingType, HttpService),
        [language, variableTypes, processingType],
    );

    const [isLoading, setIsLoading] = useState(false);
    useEffect(() => expressionSuggester.on("stateChange", setIsLoading), [expressionSuggester]);

    const [customAceEditorCompleter] = useState(() => new CustomAceEditorCompleter(expressionSuggester, nodeNameByContextKey));
    useEffect(() => customAceEditorCompleter.replaceSuggester(expressionSuggester), [customAceEditorCompleter, expressionSuggester]);
    useEffect(
        () => customAceEditorCompleter.setNodeNameByContextKey(nodeNameByContextKey),
        [customAceEditorCompleter, nodeNameByContextKey],
    );

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
