import React, { useCallback, useState } from "react";
import { Box, Fade, LinearProgress, styled } from "@mui/material";
import { cx } from "@emotion/css";
import { nodeInput, nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import { isEmpty } from "lodash";
import AceEditor from "./AceWithSettings";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { AceWrapperInputProps } from "./AceWrapper";
import { ExpressionLang } from "./types";
import ReactAce from "react-ace/lib/ace";
import { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import { FieldError } from "../Validators";
import { nodeInputCss } from "../../../../NodeInput";

type InputProps = AceWrapperInputProps & {
    language: ExpressionLang | string;
    value: string;
    onValueChange: (value: string) => void;
    ref?: React.Ref<ReactAce>;
};

export type CustomCompleterAceEditorProps = {
    completer?: CustomAceEditorCompleter;
    isLoading?: boolean;
    inputProps: InputProps;
    fieldErrors?: FieldError[];
    validationLabelInfo?: string;
    showValidation?: boolean;
    isMarked?: boolean;
    className?: string;
};

export function CustomCompleterAceEditor(props: CustomCompleterAceEditorProps): JSX.Element {
    const { className, isMarked, showValidation, fieldErrors, validationLabelInfo, completer, isLoading } = props;
    const { value, onValueChange, ref, ...inputProps } = props.inputProps;

    const [editorFocused, setEditorFocused] = useState(false);

    const onChange = useCallback((value: string) => onValueChange(value), [onValueChange]);
    const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), []);

    return (
        <Box className={cx(nodeValue, className)} sx={{ width: "100%" }}>
            <Box sx={{ position: "relative" }}>
                <Box
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
                        ref={ref}
                        value={value}
                        onChange={onChange}
                        onFocus={editorFocus(true)}
                        onBlur={editorFocus(false)}
                        inputProps={{
                            rows: 1,
                            className: nodeInput,
                            style: nodeInputCss,
                            ...inputProps,
                        }}
                        customAceEditorCompleter={completer}
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
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} validationLabelInfo={validationLabelInfo} />}
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
