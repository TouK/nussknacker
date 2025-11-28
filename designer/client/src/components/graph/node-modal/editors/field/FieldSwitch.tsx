import { css } from "@emotion/css";
import { Box, styled, Tab, Tabs } from "@mui/material";
import type { ReactNode } from "react";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { blendDarken, getBorderColor } from "../../../../../containers/theme/helpers";
import type { Option } from "../../fragment-input-definition/TypeSelect";
import { editors, isExtendedEditor } from "../expression/Editor";
import type { EditorConfig } from "../expression/EditorConfig";
import { editorsParameters } from "../expression/editorsParameters";
import type { ExpressionObj } from "../expression/types";
import { EditorType } from "../expression/types";
import { InfoTooltip } from "../InfoTooltip/InfoTooltip";
import type { ParamType } from "../types";

const StyledTab = styled(Tab)(({ theme }) => ({
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
    "&[aria-disabled='true']": {
        pointerEvents: "none",
        cursor: "default",
        background: theme.palette.action.disabledBackground,
        "&:hover": {
            background: theme.palette.action.disabledBackground,
        },
    },
    ".MuiSvgIcon-root": {
        backgroundColor: "inherit",
    },
}));

const SINGLE_EDITOR_TO_DISPLAY: EditorConfig["type"][] = [
    EditorType.CRON_EDITOR,
    EditorType.JSON_PARAMETER_EDITOR,
    EditorType.SPEL_PARAMETER_EDITOR,
    EditorType.SQL_PARAMETER_EDITOR,
    EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR,
    EditorType.DICT_PARAMETER_EDITOR,
    EditorType.JSON_TEMPLATE_PARAMETER_EDITOR,
];

interface Props {
    expressionObj: ExpressionObj;
    availableEditors: ParamType["editors"];
    onValueChange: (value: ExpressionObj) => void;
    children: ReactNode | ((selectedEditor: ParamType["editors"][number]) => ReactNode);
    readOnly?: boolean;
    showSwitch?: boolean;
}

export const FieldSwitch = ({ availableEditors, onValueChange, expressionObj, children, readOnly, showSwitch = true }: Props) => {
    const { t } = useTranslation();
    const allowsSwitch = useCallback(
        (checkedEditor: ParamType["editors"][number]) => {
            const editor = editors[checkedEditor.type];

            return isExtendedEditor(editor) ? editor.isSwitchableTo(expressionObj, checkedEditor) : true;
        },
        [expressionObj],
    );

    const getHint = useCallback(
        (checkedEditor: ParamType["editors"][number]) => {
            const editor = editors[checkedEditor.type];
            if (readOnly) {
                return t("editors.default.hint", "Switching is disabled. You are in read-only mode");
            }

            if (allowsSwitch(checkedEditor)) {
                return;
            }

            if (!isExtendedEditor(editor)) {
                return;
            }

            return editor?.notSwitchableToHint();
        },
        [readOnly, allowsSwitch, t],
    );

    const [selectedEditorType, setSelectedEditorType] = useState<EditorConfig["type"]>(
        () =>
            availableEditors.find((editor: EditorConfig) => {
                const editorParameters = editorsParameters[editor.type];

                return editorParameters?.language === expressionObj.language && allowsSwitch(editor);
            })?.type,
    );

    const selectedEditor = useMemo(
        () =>
            availableEditors.find((editor) => {
                return editor.type === selectedEditorType && allowsSwitch(editor);
            }) ?? availableEditors[0],
        [allowsSwitch, availableEditors, selectedEditorType],
    );

    const availableEditorsOptions: (Option & { hint: string | undefined })[] = useMemo(
        () =>
            availableEditors.map((editor) => ({
                label: editorsParameters[editor.type]?.displayName,
                value: editor.type,
                isDisabled: readOnly || (!allowsSwitch(editor) && editor.type !== selectedEditor.type),
                hint: editor.type !== selectedEditor.type ? getHint(editor) : undefined,
            })),
        [allowsSwitch, availableEditors, getHint, readOnly, selectedEditor.type],
    );

    const isSingleEditorVisible = availableEditors.length === 1 && !SINGLE_EDITOR_TO_DISPLAY.includes(availableEditors[0].type);

    if (readOnly || !showSwitch || isSingleEditorVisible) {
        return <>{typeof children === "function" ? children(selectedEditor) : children}</>;
    }

    const selectedOption = availableEditorsOptions.find(({ value }) => value === selectedEditor.type);
    if (!selectedOption) {
        return null;
    }

    return (
        <Box display="block" flexBasis={"60%"} flex={1} width={"100%"}>
            <Box display="flex" justifyContent="flex-end">
                <Tabs
                    value={selectedOption.value}
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
                        const editorComponent = editors[selectedEditor.type];
                        const editorWithParseValueMethod = isExtendedEditor(editorComponent) && editorComponent.parseValueOnEditorChange;

                        onValueChange(
                            editorWithParseValueMethod
                                ? editorComponent?.parseValueOnEditorChange(expressionObj, editorParameters.language)
                                : { ...expressionObj, language: editorParameters.language },
                        );
                        setSelectedEditorType(selectedEditor.type);
                    }}
                >
                    {availableEditorsOptions.map((option, index) => (
                        <StyledTab
                            aria-disabled={option.isDisabled}
                            disableTouchRipple
                            disabled={availableEditorsOptions.length <= 1}
                            key={index}
                            label={option.label?.toLowerCase()}
                            value={option.value}
                            classes={{
                                selected: css({ outline: "none" }),
                                root: css({
                                    cursor: option.value === selectedEditor.type ? "default !important" : "pointer",
                                    "&:focus": { outline: "none" },
                                }),
                                iconWrapper: css({
                                    marginLeft: "2px !important",
                                    display: "flex",
                                    alignItems: "center",
                                    pointerEvents: "auto",
                                }),
                            }}
                            iconPosition="end"
                            icon={
                                option.hint && (
                                    <div>
                                        <InfoTooltip title={option.hint} />
                                    </div>
                                )
                            }
                        />
                    ))}
                </Tabs>
            </Box>
            {typeof children === "function" ? children(selectedEditor) : children}
        </Box>
    );
};
