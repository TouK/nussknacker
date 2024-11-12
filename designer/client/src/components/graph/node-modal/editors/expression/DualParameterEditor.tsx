import {ExtendedEditor, isExtendedEditor, OnValueChange, SimpleEditor } from "./Editor";
import React, { useCallback, useMemo, useState } from "react";
import { VariableTypes } from "../../../../../types";
import { css } from "@emotion/css";
import { RawEditorIcon, SimpleEditorIcon, SwitchButton } from "./SwitchButton";
import { useTranslation } from "react-i18next";
import { FieldError } from "../Validators";
import { nodeValue } from "../../NodeDetailsContent/NodeTableStyled";

type Props = {
    readOnly?: boolean;
    valueClassName?: string;
    fieldErrors: FieldError[];
    isMarked?: boolean;
    showValidation: boolean;
    onValueChange: OnValueChange;
    className: string;
    variableTypes: VariableTypes;
    showSwitch?: boolean;
    BasicEditor: SimpleEditor | ExtendedEditor;
    ExpressionEditor: SimpleEditor | ExtendedEditor;
};

export const DualParameterEditor: SimpleEditor<Props> = (props: Props) => {
    const { BasicEditor, ExpressionEditor, readOnly, valueClassName, expressionObj } = props;
    const { t } = useTranslation();

    console.log('BasicEditor', BasicEditor)
    console.log('ExpressionEditor', ExpressionEditor)
    const showSwitch = useMemo(() => props.showSwitch && BasicEditor, [BasicEditor, props.showSwitch]);

    const simpleEditorAllowsSwitch = useMemo(
        () => isExtendedEditor(BasicEditor) && BasicEditor.isSwitchableTo(expressionObj, ExpressionEditor.displayName),
        [BasicEditor, ExpressionEditor.displayName, expressionObj],
    );

    const isExpressionEditorVisible = isExtendedEditor(BasicEditor)
        ? BasicEditor?.getExpressionMode?.(expressionObj).language === expressionObj.language
        : false;

    const initialDisplaySimple = useMemo(() => true, []);

    const [displayRawEditor, setDisplayRawEditor] = useState(!initialDisplaySimple);
    const toggleRawEditor = useCallback(() => setDisplayRawEditor((v) => !v), []);

    const disabled = useMemo(
        () => readOnly || (displayRawEditor && !simpleEditorAllowsSwitch),
        [displayRawEditor, readOnly, simpleEditorAllowsSwitch],
    );

    const hint = useMemo(() => {
        if (!displayRawEditor) {
            return t("editors.raw.switchableToHint", "Switch to expression mode");
        }

        if (readOnly) {
            return t("editors.default.hint", "Switching to basic mode is disabled. You are in read-only mode");
        }

        if (!isExtendedEditor(BasicEditor)) {
            return;
        }

        if (simpleEditorAllowsSwitch) {
            return BasicEditor?.switchableToHint();
        }

        return BasicEditor?.notSwitchableToHint();
    }, [displayRawEditor, readOnly, simpleEditorAllowsSwitch, BasicEditor, t]);

    const editorProps = useMemo(
        () => ({
            ...props,
            className: `${valueClassName ? valueClassName : nodeValue} ${showSwitch ? "switchable" : ""}`,
        }),
        [props, showSwitch, valueClassName],
    );

    const editorExpressionObj = useMemo(() => {
        if (isExtendedEditor(ExpressionEditor) && ExpressionEditor?.getExpressionMode) {
            if (displayRawEditor) {
                return ExpressionEditor?.getExpressionMode?.(props.expressionObj);
            } else {
                return ExpressionEditor?.getBasicMode?.(props.expressionObj);
            }
        }

        return props.expressionObj;
    }, [ExpressionEditor, displayRawEditor, props.expressionObj]);

    const onValueChangeWithExpressionValue = useCallback(
        (expression: string) => props.onValueChange({ expression, language: editorExpressionObj.language }),
        [editorExpressionObj.language, props],
    );

    return (
        <div
            className={css({
                display: "flex",
                flex: 1,
                gap: 5,
            })}
        >
            {displayRawEditor ? (
                <ExpressionEditor {...editorProps} expressionObj={editorExpressionObj} onValueChange={onValueChangeWithExpressionValue} />
            ) : (
                <BasicEditor {...editorProps} expressionObj={editorExpressionObj} onValueChange={onValueChangeWithExpressionValue} />
            )}
            {showSwitch ? (
                <SwitchButton onClick={toggleRawEditor} disabled={disabled} title={hint}>
                    {displayRawEditor ? <SimpleEditorIcon type={BasicEditor.displayName} /> : <RawEditorIcon />}
                </SwitchButton>
            ) : null}
        </div>
    );
};
