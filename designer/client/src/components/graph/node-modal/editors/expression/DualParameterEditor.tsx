import { DualEditorMode, editors, EditorType, ExtendedEditor, isExtendedEditor, SimpleEditor } from "./Editor";
import React, { useCallback, useMemo, useState } from "react";
import { ExpressionObj } from "./types";
import { RawEditor } from "./RawEditor";
import { VariableTypes } from "../../../../../types";
import { css } from "@emotion/css";
import { RawEditorIcon, SimpleEditorIcon, SwitchButton } from "./SwitchButton";
import { useTranslation } from "react-i18next";
import { FieldError } from "../Validators";
import ErrorBoundary from "../../../../common/ErrorBoundary";

type Props = {
    editorConfig: {
        simpleEditor: {
            type: EditorType;
        };
        defaultMode: DualEditorMode;
    };
    expressionObj: ExpressionObj;
    readOnly?: boolean;
    valueClassName?: string;
    fieldErrors: FieldError[];
    isMarked?: boolean;
    showValidation: boolean;
    onValueChange: (value: string) => void;
    className: string;
    variableTypes: VariableTypes;
    showSwitch?: boolean;
};

export const DualParameterEditor: SimpleEditor<Props> = (props: Props) => {
    const { editorConfig, readOnly, valueClassName, expressionObj } = props;
    const { t } = useTranslation();

    const Editor: SimpleEditor | ExtendedEditor = useMemo(() => editors[editorConfig.simpleEditor.type], [editorConfig.simpleEditor.type]);

    const showSwitch = useMemo(() => props.showSwitch && Editor, [Editor, props.showSwitch]);

    const simpleEditorAllowsSwitch = useMemo(
        () => isExtendedEditor(Editor) && Editor.isSwitchableTo(expressionObj, editorConfig.simpleEditor),
        [Editor, editorConfig.simpleEditor, expressionObj],
    );

    const initialDisplaySimple = useMemo(
        () => editorConfig.defaultMode === DualEditorMode.SIMPLE && simpleEditorAllowsSwitch,
        [editorConfig.defaultMode, simpleEditorAllowsSwitch],
    );

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

        if (!isExtendedEditor(Editor)) {
            return;
        }

        if (simpleEditorAllowsSwitch) {
            return Editor?.switchableToHint();
        }

        return Editor?.notSwitchableToHint();
    }, [displayRawEditor, readOnly, simpleEditorAllowsSwitch, Editor, t]);

    const editorProps = useMemo(
        () => ({
            ...props,
            className: `${valueClassName ? valueClassName : "node-value"} ${showSwitch ? "switchable" : ""}`,
        }),
        [props, showSwitch, valueClassName],
    );

    return (
        <div
            className={css({
                display: "flex",
                flex: 1,
                gap: 5,
            })}
        >
            <ErrorBoundary>
                {displayRawEditor ? <RawEditor {...editorProps} /> : <Editor {...editorProps} editorConfig={editorConfig.simpleEditor} />}
            </ErrorBoundary>
            {showSwitch ? (
                <SwitchButton onClick={toggleRawEditor} disabled={disabled} title={hint}>
                    {displayRawEditor ? <SimpleEditorIcon type={editorConfig.simpleEditor.type} /> : <RawEditorIcon />}
                </SwitchButton>
            ) : null}
        </div>
    );
};
