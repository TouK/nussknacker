import { DualEditorMode, editors, EditorType, SimpleEditor } from "./Editor";
import React, { useCallback, useMemo, useState } from "react";
import { ExpressionObj } from "./types";
import RawEditor from "./RawEditor";
import { VariableTypes } from "../../../../../types";
import { css } from "@emotion/css";
import { RawEditorIcon, SimpleEditorIcon, SwitchButton } from "./SwitchButton";
import { useTranslation } from "react-i18next";
import { Validator } from "../Validators";

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

    validators?: Validator[];
    isMarked?: boolean;
    showValidation?: boolean;
    onValueChange: (value: string) => void;
    className: string;
    variableTypes: VariableTypes;
    showSwitch?: boolean;
};

export default function DualParameterEditor(props: Props): JSX.Element {
    const { editorConfig, readOnly, valueClassName, expressionObj } = props;
    const { t } = useTranslation();

    const SimpleEditor = useMemo(
        () =>
            editors[editorConfig.simpleEditor.type] as SimpleEditor<{
                onValueChange: (value: string) => void;
                editorConfig?: unknown;
            }>,
        [editorConfig.simpleEditor.type],
    );

    const showSwitch = useMemo(() => props.showSwitch && SimpleEditor, [SimpleEditor, props.showSwitch]);

    const simpleEditorAllowsSwitch = useMemo(
        () => SimpleEditor?.isSwitchableTo(expressionObj, editorConfig.simpleEditor),
        [SimpleEditor, editorConfig.simpleEditor, expressionObj],
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

        if (simpleEditorAllowsSwitch) {
            return SimpleEditor?.switchableToHint();
        }

        return SimpleEditor?.notSwitchableToHint();
    }, [displayRawEditor, readOnly, simpleEditorAllowsSwitch, SimpleEditor, t]);

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
            {displayRawEditor ? <RawEditor {...editorProps} /> : <SimpleEditor {...editorProps} editorConfig={editorConfig.simpleEditor} />}
            {showSwitch ? (
                <SwitchButton onClick={toggleRawEditor} disabled={disabled} title={hint}>
                    {displayRawEditor ? <SimpleEditorIcon type={editorConfig.simpleEditor.type} /> : <RawEditorIcon />}
                </SwitchButton>
            ) : null}
        </div>
    );
}
