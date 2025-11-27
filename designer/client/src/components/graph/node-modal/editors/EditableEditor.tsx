import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import { isEmpty } from "lodash";
import type { ReactNode } from "react";
import React, { forwardRef, useMemo } from "react";

import type { TypingResult } from "../../../../types/definition";
import type { VariableTypes } from "../../../../types/validation";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import type { OnValueChange } from "./expression/Editor";
import { editors as editorsClasses } from "./expression/Editor";
import { spelFormatters } from "./expression/Formatter";
import type { ExpressionObj } from "./expression/types";
import { EditorType, ExpressionLang } from "./expression/types";
import { FieldSwitch } from "./field/FieldSwitch";
import { FormControl, FormLabel } from "./FormControl";
import { FieldLabelConsumer } from "./RenderFieldLabel";
import type { Editor } from "./types";
import type { FieldError, PossibleValue } from "./Validators";

interface Props {
    expressionObj: ExpressionObj;
    showSwitch?: boolean;
    fieldLabel?: string;
    readOnly?: boolean;
    valueClassName?: string;
    editors?: Editor[];
    paramType?: TypingResult;
    values?: Array<PossibleValue>;
    isMarked?: boolean;
    showValidation?: boolean;
    onValueChange: OnValueChange;
    fieldErrors?: FieldError[];
    variableTypes: VariableTypes;
    validationLabelInfo?: ReactNode;
    placeholder?: string;
    endAdornment?: ReactNode;
    defaultValue?: ExpressionObj | string;
}

export const EditableEditor = forwardRef((props: Props, ref) => {
    const { expressionObj, valueClassName, editors, paramType, fieldErrors = [], validationLabelInfo } = props;

    const availableEditors: Editor[] = useMemo(
        (): Editor[] => (isEmpty(editors) ? [{ type: EditorType.SPEL_PARAMETER_EDITOR }] : editors),
        [editors],
    );

    const formatter = useMemo(
        () => (expressionObj?.language === ExpressionLang.SpEL ? spelFormatters[paramType?.refClazzName] : null),
        [expressionObj?.language, paramType?.refClazzName],
    );

    return (
        <FieldSwitch
            availableEditors={availableEditors}
            expressionObj={expressionObj}
            onValueChange={props.onValueChange}
            readOnly={props.readOnly}
            showSwitch={props.showSwitch}
        >
            {(selectedEditor) => {
                const Editor = editorsClasses[selectedEditor.type];

                return (
                    <Editor
                        {...props}
                        ref={ref}
                        editorConfig={selectedEditor}
                        className={`${valueClassName ? valueClassName : nodeValue}`}
                        fieldErrors={fieldErrors}
                        formatter={formatter}
                        expressionInfo={validationLabelInfo}
                    />
                );
            }}
        </FieldSwitch>
    );
});

EditableEditor.displayName = "EditableEditor";

function EditableEditorRow({
    rowClassName,

    fieldLabel,
    endAdornment,
    ...props
}: Props & {
    rowClassName?: string;
}): React.JSX.Element {
    return (
        <FormControl
            className={cx(rowClassName && rowClassName)}
            sx={{
                width: "100%",
                margin: rowClassName && 0,
            }}
        >
            <>
                {fieldLabel ? <FieldLabelConsumer text={fieldLabel} /> : <FormLabel />}
                <EditableEditor {...props} />
                {endAdornment && <EndAdornment>{endAdornment}</EndAdornment>}
            </>
        </FormControl>
    );
}

const EndAdornment = styled("div")(({ theme }) => ({
    display: "flex",
    justifyContent: "center",
    alignItems: "flex-start",
    marginTop: theme.spacing(0.5),
    marginLeft: theme.spacing(0.5),
}));

export default EditableEditorRow;
