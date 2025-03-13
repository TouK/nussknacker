import React, { useState, ReactNode, useMemo, useCallback } from "react";
import { ExpressionObj } from "../expression/types";
import { Option } from "../../fragment-input-definition/TypeSelect";
import { Box, Tabs, Tab } from "@mui/material";
import { css } from "@emotion/css";
import { ParamType } from "../types";
import { editorsParameters } from "../expression/editorsParameters";
import { blendDarken, getBorderColor } from "../../../../../containers/theme/helpers";
import { editors, isExtendedEditor } from "../expression/Editor";

interface Props {
    expressionObj: ExpressionObj;
    availableEditors: ParamType["editors"];
    onValueChange: (value: ExpressionObj) => void;
    children: ReactNode | ((selectedEditor: ParamType["editors"][number]) => ReactNode);
    readOnly: boolean;
}

export const FieldSwitch = ({ availableEditors, onValueChange, expressionObj, children, readOnly }: Props) => {
    const allowsSwitch = useCallback(
        (verifiedEditor: ParamType["editors"][number]) => {
            const editor = editors[verifiedEditor.type];

            return isExtendedEditor(editor) ? editor.isSwitchableTo(expressionObj, verifiedEditor) : true;
        },
        [expressionObj],
    );

    const [selectedEditor, setSelectedEditor] = useState(
        availableEditors.find((editor) => {
            const editorParameters = editorsParameters[editor.type];

            return editorParameters.language === expressionObj.language && allowsSwitch(editor);
        }) ?? availableEditors[0],
    );

    const availableEditorsOptions: Option[] = useMemo(
        () =>
            availableEditors.map((editor) => ({
                label: editorsParameters[editor.type].displayName,
                value: editor.type,
                isDisabled: readOnly || !allowsSwitch(editor),
            })),
        [allowsSwitch, availableEditors, readOnly],
    );

    return (
        <Box display="block" flexBasis={"80%"} width={"100%"}>
            <Box display="flex" justifyContent="flex-end">
                <Tabs
                    value={
                        availableEditorsOptions.find((availableEditorsOption) => availableEditorsOption.value === selectedEditor.type)
                            ?.value
                    }
                    variant="standard"
                    scrollButtons="auto"
                    sx={{
                        minHeight: "20px",
                        minWidth: "45px",
                        marginRight: "-1px", // fix for border, to keep it align to the input right outline
                    }}
                    TabIndicatorProps={{ sx: { display: "none" } }}
                    onChange={(_, value: string) => {
                        const selectedEditor = availableEditors.find((editor) => editor.type === value);
                        const editorParameters = editorsParameters[selectedEditor.type];
                        onValueChange({ ...expressionObj, language: editorParameters.language });
                        setSelectedEditor(availableEditors.find((availableEditorsOption) => availableEditorsOption.type === value));
                    }}
                >
                    {availableEditorsOptions.map((option, index) => (
                        <Tab
                            disabled={option.isDisabled}
                            disableFocusRipple
                            disableRipple
                            disableTouchRipple
                            key={index}
                            label={option.label.toLowerCase()}
                            value={option.value}
                            classes={{
                                selected: css({ outline: "none" }),
                                root: css({ "&:focus": { outline: "none" } }),
                            }}
                            sx={(theme) => ({
                                fontSize: "0.65rem",
                                padding: theme.spacing(0, 0.5),
                                textTransform: "none",
                                minHeight: "20px",
                                minWidth: "45px",
                                border: `1px solid ${getBorderColor(theme)}`,
                                "&.Mui-selected": {
                                    color: theme.palette.text.primary,
                                    background: blendDarken(theme.palette.primary.main, 0.3),
                                },
                            })}
                        />
                    ))}
                </Tabs>
            </Box>
            {typeof children === "function" ? children(selectedEditor) : children}
        </Box>
    );
};

// export const DualParameterEditor: SimpleEditor<Props> = (props: Props) => {
//     const { editorConfig, readOnly, valueClassName, expressionObj } = props;
//     const { t } = useTranslation();
//
//     const Editor: SimpleEditor | ExtendedEditor = useMemo(() => editors[editorConfig.simpleEditor.type], [editorConfig.simpleEditor.type]);
//
//     const showSwitch = useMemo(() => props.showSwitch && Editor, [Editor, props.showSwitch]);
//
//     const simpleEditorAllowsSwitch = useMemo(
//         () => isExtendedEditor(Editor) && Editor.isSwitchableTo(expressionObj, editorConfig.simpleEditor),
//         [Editor, editorConfig.simpleEditor, expressionObj],
//     );
//
//     const isExpressionEditorVisible = isExtendedEditor(Editor)
//         ? Editor?.getExpressionMode?.(expressionObj).language === expressionObj.language
//         : false;
//
//     const initialDisplaySimple = useMemo(
//         () => editorConfig.defaultMode === DualEditorMode.SIMPLE && simpleEditorAllowsSwitch && !isExpressionEditorVisible,
//         [editorConfig.defaultMode, isExpressionEditorVisible, simpleEditorAllowsSwitch],
//     );
//
//     const [displayRawEditor, setDisplayRawEditor] = useState(!initialDisplaySimple);
//     const toggleRawEditor = useCallback(() => setDisplayRawEditor((v) => !v), []);
//
//     const disabled = useMemo(
//         () => readOnly || (displayRawEditor && !simpleEditorAllowsSwitch),
//         [displayRawEditor, readOnly, simpleEditorAllowsSwitch],
//     );
//
//     const hint = useMemo(() => {
//         if (!displayRawEditor) {
//             return t("editors.raw.switchableToHint", "Switch to expression mode");
//         }
//
//         if (readOnly) {
//             return t("editors.default.hint", "Switching to basic mode is disabled. You are in read-only mode");
//         }
//
//         if (!isExtendedEditor(Editor)) {
//             return;
//         }
//
//         if (simpleEditorAllowsSwitch) {
//             return Editor?.switchableToHint();
//         }
//
//         return Editor?.notSwitchableToHint();
//     }, [displayRawEditor, readOnly, simpleEditorAllowsSwitch, Editor, t]);
//
//     const editorProps = useMemo(
//         () => ({
//             ...props,
//             className: `${valueClassName ? valueClassName : nodeValue} ${showSwitch ? "switchable" : ""}`,
//         }),
//         [props, showSwitch, valueClassName],
//     );
//
//     const editorExpressionObj = useMemo(() => {
//         if (isExtendedEditor(Editor) && Editor?.getExpressionMode) {
//             if (displayRawEditor) {
//                 return Editor?.getExpressionMode?.(props.expressionObj);
//             } else {
//                 return Editor?.getBasicMode?.(props.expressionObj);
//             }
//         }
//
//         return props.expressionObj;
//     }, [Editor, displayRawEditor, props.expressionObj]);
//
//     const onValueChangeWithExpressionValue = useCallback(
//         (expression: string) => props.onValueChange({ expression, language: editorExpressionObj.language }),
//         [editorExpressionObj.language, props],
//     );
//
//     return (
//         <div
//             className={css({
//                 display: "flex",
//                 flex: 1,
//                 gap: 5,
//             })}
//         >
//             {displayRawEditor ? (
//                 <RawEditor {...editorProps} expressionObj={editorExpressionObj} onValueChange={onValueChangeWithExpressionValue} />
//             ) : (
//                 <Editor
//                     {...editorProps}
//                     editorConfig={editorConfig.simpleEditor}
//                     expressionObj={editorExpressionObj}
//                     onValueChange={onValueChangeWithExpressionValue}
//                 />
//             )}
//             {showSwitch ? (
//                 <SwitchButton onClick={toggleRawEditor} disabled={disabled} title={hint}>
//                     {displayRawEditor ? <SimpleEditorIcon type={editorConfig.simpleEditor.type} /> : <RawEditorIcon />}
//                 </SwitchButton>
//             ) : null}
//         </div>
//     );
// };
