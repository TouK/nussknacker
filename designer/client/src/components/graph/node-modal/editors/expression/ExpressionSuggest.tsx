import "ace-builds/src-noconflict/ace";
import { isEmpty, isEqual } from "lodash";
import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../../../reducers/selectors/settings";
import { getProcessingType } from "../../../../../reducers/selectors/graph";
import { BackendExpressionSuggester } from "./ExpressionSuggester";
import HttpService from "../../../../../http/HttpService";
import AceEditor from "./AceWithSettings";
import ValidationLabels from "../../../../modals/ValidationLabels";
import ReactAce from "react-ace/lib/ace";
import { EditorMode, ExpressionLang } from "./types";
import { SerializedStyles } from "@emotion/react";
import { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import { cx } from "@emotion/css";
import { VariableTypes } from "../../../../../types";
import { FieldError } from "../Validators";
import { nodeInputWithError, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import { Box, Fade, LinearProgress, styled } from "@mui/material";

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
    fieldErrors: FieldError[];
    validationLabelInfo: string;
    showValidation?: boolean;
    isMarked?: boolean;
    variableTypes: VariableTypes;
    editorMode?: EditorMode;
}

const ExpressionSuggestRow = styled("div")({});

function useDeepMemo<T>(factory: () => T, deps: React.DependencyList): T {
    const ref = useRef<{ value: T; deps: React.DependencyList }>();

    if (!ref.current || !isEqual(deps, ref.current.deps)) {
        ref.current = { value: factory(), deps };
    }

    return ref.current.value;
}

export function ExpressionSuggest(props: Props): JSX.Element {
    const { isMarked, showValidation, inputProps, fieldErrors, variableTypes, validationLabelInfo } = props;

    const definitionData = useSelector(getProcessDefinitionData);
    const dataResolved = !isEmpty(definitionData);
    const processingType = useSelector(getProcessingType);

    const { value, onValueChange, language } = inputProps;
    const [editorFocused, setEditorFocused] = useState(false);

    const expressionSuggester = useDeepMemo(
        () => new BackendExpressionSuggester(language, variableTypes, processingType, HttpService),
        [language, variableTypes, processingType],
    );

    const [isLoading, setIsLoading] = useState(false);
    useEffect(() => expressionSuggester.on("stateChange", setIsLoading), [expressionSuggester]);

    const [customAceEditorCompleter] = useState(() => new CustomAceEditorCompleter(expressionSuggester));
    useEffect(() => customAceEditorCompleter.replaceSuggester(expressionSuggester), [customAceEditorCompleter, expressionSuggester]);

    const onChange = useCallback((value: string) => onValueChange(value), [onValueChange]);
    const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), []);

    return dataResolved ? (
        <>
            <Box sx={{ position: "relative" }}>
                <ExpressionSuggestRow
                    className={cx([
                        rowAceEditor,
                        showValidation && !isEmpty(fieldErrors) && nodeInputWithError,
                        isMarked && "marked",
                        editorFocused && "focused",
                        inputProps.readOnly && "read-only",
                    ])}
                    sx={{ position: "relative" }}
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
                </ExpressionSuggestRow>
                <Fade
                    in={isLoading}
                    unmountOnExit
                    style={{
                        transitionDelay: isLoading ? ".25s" : "0s",
                    }}
                >
                    <LoadingFeedback color="warning" inflate={0.25} />
                </Fade>
            </Box>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} validationLabelInfo={validationLabelInfo} />}
        </>
    ) : null;
}

const LoadingFeedback = styled(LinearProgress)<{
    inflate?: number;
}>(({ inflate = 0 }) => {
    const outside = inflate + 1;
    const inside = 2 * inflate + 1;
    return {
        position: "absolute",
        top: -outside,
        bottom: -outside,
        left: -outside,
        right: -outside,
        height: "auto",
        clipPath: `polygon(0% 0%, 0% 100%, ${inside}px 100%, ${inside}px ${inside}px, calc(100% - ${inside}px) ${inside}px, calc(100% - ${inside}px) calc(100% - ${inside}px), ${inside}px calc(100% - ${inside}px), ${inside}px 100%, 100% 100%, 100% 0%)`,
        opacity: 0.25,
    };
});
