import { cx } from "@emotion/css";
import { Box, Fade, LinearProgress, styled } from "@mui/material";
import type { ReactNode } from "react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import type ReactAce from "react-ace/lib/ace";
import { useMergeRefs } from "rooks";

import { useUserSettings } from "../../../../../common/useUserSettings";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputCss } from "../../../../NodeInput";
import { nodeInput, nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import type { FieldError } from "../Validators";
import { setupAceEditorSnippets } from "./AceEditorJsonBasedSnippets";
import AceWithSettings from "./AceWithSettings";
import type { AceWrapperInputProps } from "./AceWrapper";
import type { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import type { ExpressionLang } from "./types";
import { useAceEditorRangeMessages } from "./useAceEditorRangeMessages";
import { useCompletionsVisible } from "./useCompletionsVisible";
import { useValidationInfoVisibility } from "./useValidationInfoVisibility";

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
    isValidating?: boolean;
    inputProps: InputProps;
    fieldErrors?: FieldError[];
    validationLabelInfo?: ReactNode;
    showValidation?: boolean;
    isMarked?: boolean;
    className?: string;
    enableLiveAutocompletion?: boolean;
};

export function CustomCompleterAceEditor(props: CustomCompleterAceEditorProps): React.JSX.Element {
    const {
        className,
        isMarked,
        showValidation,
        fieldErrors,
        validationLabelInfo,
        completer,
        isLoading,
        isValidating,
        enableLiveAutocompletion,
    } = props;
    const { value, onValueChange, ref, ...inputProps } = props.inputProps;
    const editorRef = useRef<ReactAce>();
    const mergedRefs = useMergeRefs(ref, editorRef);
    const [editorFocused, setEditorFocused] = useState(false);
    const [internalValue, setInternalValue] = useState(value);

    const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), []);

    const [showLines] = useUserSettings(`editor.${props.inputProps.language}.showLines`);

    const completionsVisible = useCompletionsVisible({
        editorRef,
        isLoading,
        onInternalValueChange: setInternalValue,
    });

    const { visibleValidationErrors, visibleValidationLabelInfo } = useValidationInfoVisibility({
        fieldErrors,
        showValidation,
        completionsVisible,
        isValidating,
        isLoading,
        validationLabelInfo,
    });

    const { annotations, markers, hasRangeText } = useAceEditorRangeMessages(visibleValidationErrors, showLines);

    useEffect(
        function updateValueWhenCompletionsClosed() {
            if (props.inputProps?.readOnly) return;
            if (completionsVisible) return;
            onValueChange(internalValue);
        },
        [internalValue, completionsVisible, onValueChange, props.inputProps?.readOnly],
    );

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
                        visibleValidationErrors.length > 0 && nodeInputWithError,
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
                        fieldErrors={visibleValidationErrors}
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
                <ValidationLabels fieldErrors={visibleValidationErrors} validationLabelInfo={visibleValidationLabelInfo} />
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
