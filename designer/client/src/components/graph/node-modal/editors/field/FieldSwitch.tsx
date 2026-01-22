import { Box, styled, Tab, Tabs } from "@mui/material";
import type { ReactNode } from "react";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { blendDarken, getBorderColor } from "../../../../../containers/theme/helpers";
import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { Option } from "../../fragment-input-definition/TypeSelect";
import { isExtendedEditor } from "../expression/Editor";
import { getEditorByType, parseExpressionObjForType } from "../expression/EditorByType";
import type { EditorConfig, EditorConfigForType } from "../expression/EditorConfig";
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
    "&:focus": { outline: "none" },

    "&.Mui-selected": {
        color: theme.palette.text.primary,
        background: blendDarken(theme.palette.primary.main, 0.3),
        outline: "none",
    },
    "&[aria-disabled='true']": {
        cursor: "default",
        background: theme.palette.action.disabledBackground,
        "&:hover": {
            background: theme.palette.action.disabledBackground,
        },
    },
    ".MuiTab-iconWrapper": {
        marginLeft: "2px !important",
        display: "flex",
        alignItems: "center",
        pointerEvents: "auto",
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

export type CallbackChildren = (type: EditorType, editorConfig: Omit<EditorConfigForType<typeof type>, "type">) => ReactNode;

interface Props {
    expressionObj: ExpressionObj;
    availableEditors: ParamType["editors"];
    onValueChange: (value: ExpressionObj) => void;
    children: ReactNode | CallbackChildren;
    readOnly?: boolean;
    showSwitch?: boolean;
}

export const FieldSwitch = ({ availableEditors: editors, onValueChange, expressionObj, children, readOnly, showSwitch = true }: Props) => {
    const { t } = useTranslation();
    const userSettings = useAppSelector(getUserSettings);
    const forceSpelEditors = userSettings["debug.editor.forceSpelEditors"];

    const availableEditors = useMemo(() => {
        if (forceSpelEditors && !editors.find(({ type }) => type === EditorType.SPEL_PARAMETER_EDITOR)) {
            const spel: EditorConfig = { type: EditorType.SPEL_PARAMETER_EDITOR, debug: true };
            return [...editors, spel];
        }
        return editors;
    }, [editors, forceSpelEditors]);

    const allowsSwitch = useCallback(
        (checkedEditor: ParamType["editors"][number]) => {
            const editor = getEditorByType(checkedEditor.type);

            return isExtendedEditor(editor) ? editor.isSwitchableTo(expressionObj, checkedEditor) : true;
        },
        [expressionObj],
    );

    const getHint = useCallback(
        (checkedEditor: ParamType["editors"][number]) => {
            const editor = getEditorByType(checkedEditor.type);
            if (readOnly) {
                return t("editors.default.hint", "Switching is disabled. You are in read-only mode");
            }

            if (allowsSwitch(checkedEditor)) {
                return;
            }

            if (isExtendedEditor(editor)) {
                return editor.notSwitchableToHint?.();
            }
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

    const { type, config } = useMemo(() => {
        const { type, ...config } =
            availableEditors.find((editor) => {
                return editor.type === selectedEditorType && allowsSwitch(editor);
            }) ?? availableEditors[0];
        return { type, config };
    }, [allowsSwitch, availableEditors, selectedEditorType]);

    const availableEditorsOptions: (Option & { hint: string | undefined; debug?: boolean })[] = useMemo(
        () =>
            availableEditors.map((editor) => ({
                label: editorsParameters[editor.type]?.displayName,
                value: editor.type,
                isDisabled: readOnly || (!allowsSwitch(editor) && editor.type !== type),
                hint: editor.type !== type ? getHint(editor) : undefined,
                debug: editor.debug,
            })),
        [allowsSwitch, availableEditors, getHint, readOnly, type],
    );

    const isSingleEditorHidden = availableEditors.length === 1 && !SINGLE_EDITOR_TO_DISPLAY.includes(availableEditors[0].type);

    const element = useMemo(() => (typeof children === "function" ? children(type, config) : children), [children, config, type]);

    if (readOnly || isSingleEditorHidden || (!showSwitch && !forceSpelEditors)) {
        return <Box sx={{ flex: 1 }}>{element}</Box>;
    }

    const selectedOption = availableEditorsOptions.find(({ value }) => value === type);
    if (!selectedOption) {
        return null;
    }

    return (
        <Box sx={{ flex: 1 }}>
            <Box
                sx={{
                    display: "flex",
                    justifyContent: "flex-end",
                }}
            >
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
                        const type = selectedEditor.type;
                        onValueChange(parseExpressionObjForType(type, expressionObj));
                        setSelectedEditorType(type);
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
                            sx={(theme) => ({
                                cursor: option.value === type ? "default !important" : "pointer",
                                "&": option.debug
                                    ? {
                                          color: theme.palette.info.light,
                                          "&.Mui-selected": {
                                              backgroundColor: theme.palette.info.main,
                                              color: theme.palette.info.contrastText,
                                          },
                                          "&:not(.Mui-selected):hover": {
                                              background: theme.palette.info.dark,
                                              color: theme.palette.info.contrastText,
                                          },
                                      }
                                    : null,
                            })}
                            iconPosition="end"
                            onClickCapture={(event) => {
                                if (event.target !== event.currentTarget) return;
                                if (userSettings["editor.allowForceSwitch"] && (event.altKey || event.ctrlKey || event.metaKey)) return;
                                if (!option.isDisabled) return;
                                event.stopPropagation();
                            }}
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
            {element}
        </Box>
    );
};
