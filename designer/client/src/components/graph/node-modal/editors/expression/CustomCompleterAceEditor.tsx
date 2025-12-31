import { cx } from "@emotion/css";
import { Box, Fade, LinearProgress, styled } from "@mui/material";
import { isEmpty } from "lodash";
import type { ReactNode } from "react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import type ReactAce from "react-ace/lib/ace";
import { useDebouncedValue, useDebounceFn, useMergeRefs } from "rooks";

import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../store/storeHelpers";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputCss } from "../../../../NodeInput";
import { useCallbackRef } from "../../node/useCallbackRef";
import { nodeInput, nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import type { FieldError } from "../Validators";
import { setupAceEditorSnippets } from "./AceEditorJsonBasedSnippets";
import AceWithSettings from "./AceWithSettings";
import type { AceWrapperInputProps } from "./AceWrapper";
import type { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import type { ExpressionLang } from "./types";
import { useAceEditorRangeMessages } from "./useAceEditorRangeMessages";

type InputProps = AceWrapperInputProps & {
    language: ExpressionLang | string;
    value: string;
    onValueChange: (value: string) => void;
    ref?: React.Ref<ReactAce>;
    useAceWorker?: boolean;
};

export type CustomCompleterAceEditorProps = {
    completer?: CustomAceEditorCompleter;
    isLoading?: boolean;
    inputProps: InputProps;
    fieldErrors?: FieldError[];
    validationLabelInfo?: ReactNode;
    showValidation?: boolean;
    isMarked?: boolean;
    className?: string;
    enableLiveAutocompletion?: boolean;
};

function getCompletionsActivated(editorRef: React.MutableRefObject<ReactAce | undefined>) {
    return Boolean(editorRef.current?.editor.completer?.activated);
}

export function CustomCompleterAceEditor(props: CustomCompleterAceEditorProps): React.JSX.Element {
    const { className, isMarked, showValidation, fieldErrors, validationLabelInfo, completer, isLoading, enableLiveAutocompletion } = props;
    const { value, onValueChange, ref, ...inputProps } = props.inputProps;
    const editorRef = useRef<ReactAce>();
    const mergedRefs = useMergeRefs(ref, editorRef);
    const [editorFocused, setEditorFocused] = useState(false);
    const [internalValue, setInternalValue] = useState(value);

    const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), []);

    const settings = useAppSelector(getUserSettings);
    const showLines = Boolean(settings[`editor.${props.inputProps.language}.showLines`]);

    const [completionsVisible, setCompletionsVisible] = useState(false);
    const [errorsToDisplay, setErrorsToDisplay] = useState(fieldErrors);
    useEffect(() => {
        setErrorsToDisplay(getCompletionsActivated(editorRef) ? [] : fieldErrors);
    }, [fieldErrors]);

    const [debouncedErrorsToDisplay] = useDebouncedValue(errorsToDisplay, 200);

    const { annotations, markers, hasRangeText } = useAceEditorRangeMessages(debouncedErrorsToDisplay, showLines);

    const [debouncedChange] = useDebounceFn((value: string, completionsVisible?: boolean) => {
        if (completionsVisible && getCompletionsActivated(editorRef)) return;
        return onValueChange(value);
    }, 500);

    useEffect(() => {
        debouncedChange(internalValue, completionsVisible);
    }, [completionsVisible, debouncedChange, internalValue]);

    const [onValueChangeRef] = useCallbackRef(onValueChange, [onValueChange]);
    useEffect(() => {
        const editor = editorRef.current?.editor;
        if (!editor || !editorFocused) return;
        const callback = () => {
            setCompletionsVisible(getCompletionsActivated(editorRef));
        };
        editor.on("keyboardActivity" as any, callback);
        return () => {
            editor.off("keyboardActivity" as any, callback);
        };
    }, [editorFocused]);

    useEffect(() => {
        const editor = editorRef.current?.editor;
        if (!editor) return;
        const callback = () => {
            setInternalValue(editor.getValue());
            onValueChangeRef.current(editor.getValue());
        };
        editor.on("blur", callback);
        return () => {
            editor.off("blur", callback);
        };
    }, [onValueChangeRef]);

    useEffect(() => {
        if (!editorFocused) {
            setInternalValue((current) => (current !== value ? value : current));
        }
    }, [editorFocused, value]);

    return (
        <Box className={cx(nodeValue, className)} sx={{ width: "100%" }}>
            <Box sx={{ position: "relative" }}>
                <Box
                    className={cx([
                        rowAceEditor,
                        showValidation && !isEmpty(debouncedErrorsToDisplay) && nodeInputWithError,
                        isMarked && "marked",
                        editorFocused && "focused",
                        inputProps.readOnly && "read-only",
                    ])}
                    sx={{ position: "relative" }}
                >
                    <AceWithSettings
                        onLoad={(editor) => {
                            setupAceEditorSnippets(editor);
                        }}
                        ref={mergedRefs}
                        value={internalValue}
                        onChange={setInternalValue}
                        onFocus={editorFocus(true)}
                        onBlur={editorFocus(false)}
                        inputProps={{
                            rows: 1,
                            className: nodeInput,
                            style: nodeInputCss,
                            ...inputProps,
                        }}
                        customAceEditorCompleter={completer}
                        enableLiveAutocompletion={enableLiveAutocompletion}
                        fieldErrors={debouncedErrorsToDisplay}
                        annotations={annotations}
                        markers={markers}
                    />
                </Box>
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
            {showValidation && !hasRangeText ? (
                <ValidationLabels fieldErrors={debouncedErrorsToDisplay} validationLabelInfo={validationLabelInfo} />
            ) : null}
        </Box>
    );
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
