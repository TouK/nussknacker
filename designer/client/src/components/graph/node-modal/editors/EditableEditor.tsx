import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import { isEmpty } from "lodash";
import type { ReactNode } from "react";
import React, { useMemo } from "react";

import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { TypingResult } from "../../../../types/definition";
import type { VariableTypes } from "../../../../types/validation";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import type { OnValueChange } from "./expression/Editor";
import { EditorByType } from "./expression/EditorByType";
import type { EditorConfig } from "./expression/EditorConfig";
import { spelFormatters } from "./expression/Formatter";
import type { ExpressionObj } from "./expression/types";
import { EditorType, ExpressionLang } from "./expression/types";
import { FieldSwitch } from "./field/FieldSwitch";
import { FormControl, FormLabel } from "./FormControl";
import { FieldLabelConsumer } from "./RenderFieldLabel";
import type { FieldError, PossibleValue } from "./Validators";

interface Props {
    expressionObj: ExpressionObj;
    showSwitch?: boolean;
    fieldLabel?: string;
    readOnly?: boolean;
    valueClassName?: string;
    editors?: EditorConfig[];
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

export const EditableEditor = (props: Props) => {
    const { expressionObj, valueClassName, editors, paramType, fieldErrors = [] } = props;

    const userSettings = useAppSelector(getUserSettings);
    const forceSpelEditors = userSettings["debug.editor.forceSpelEditors"];

    const availableEditors: EditorConfig[] = useMemo((): EditorConfig[] => {
        if (isEmpty(editors)) {
            return [{ type: EditorType.SPEL_PARAMETER_EDITOR }];
        }
        if (forceSpelEditors && !editors.find(({ type }) => type === EditorType.SPEL_PARAMETER_EDITOR)) {
            return [...editors, { type: EditorType.SPEL_PARAMETER_EDITOR }];
        }
        return editors;
    }, [editors, forceSpelEditors]);

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
            {({ type, ...editorConfig }) => (
                <EditorByType
                    type={type}
                    config={editorConfig}
                    {...props}
                    className={`${valueClassName ? valueClassName : nodeValue}`}
                    fieldErrors={fieldErrors}
                    formatter={formatter}
                />
            )}
        </FieldSwitch>
    );
};

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
