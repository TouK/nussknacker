import React, { useState, ReactNode, useMemo, useCallback } from "react";
import { ExpressionObj } from "../expression/types";
import { Option } from "../../fragment-input-definition/TypeSelect";
import { Box, Tabs, Tab } from "@mui/material";
import { css } from "@emotion/css";
import { ParamType } from "../types";
import { editorsParameters } from "../expression/editorsParameters";
import { blendDarken, getBorderColor } from "../../../../../containers/theme/helpers";
import { editors, isExtendedEditor } from "../expression/Editor";
import { useTranslation } from "react-i18next";
import { InfoTooltip } from "../expression/InfoTooltip";

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

    const [selectedEditor, setSelectedEditor] = useState(
        availableEditors.find((editor) => {
            const editorParameters = editorsParameters[editor.type];

            return editorParameters.language === expressionObj.language && allowsSwitch(editor);
        }) ?? availableEditors[0],
    );

    const availableEditorsOptions: (Option & { hint: string | undefined })[] = useMemo(
        () =>
            availableEditors.map((editor) => ({
                label: editorsParameters[editor.type].displayName,
                value: editor.type,
                isDisabled: readOnly || (!allowsSwitch(editor) && editor.type !== selectedEditor.type),
                hint: editor.type !== selectedEditor.type ? getHint(editor) : undefined,
            })),
        [allowsSwitch, availableEditors, getHint, readOnly, selectedEditor.type],
    );

    if (readOnly || !showSwitch) {
        return <>{typeof children === "function" ? children(selectedEditor) : children}</>;
    }
    return (
        <Box display="block" flexBasis={"60%"} flex={1} width={"100%"}>
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
                        const editorWithParseValueMethod = availableEditors.find((availableEditor) => {
                            const editorToVerification = editors[availableEditor.type];
                            return isExtendedEditor(editorToVerification) && Boolean(editorToVerification.parseValueOnEditorChange);
                        });
                        const editorComponent = editors[editorWithParseValueMethod?.type];

                        onValueChange(
                            editorComponent && isExtendedEditor(editorComponent)
                                ? editorComponent?.parseValueOnEditorChange(expressionObj, editorParameters.language)
                                : { ...expressionObj, language: editorParameters.language },
                        );
                        setSelectedEditor(availableEditors.find((availableEditorsOption) => availableEditorsOption.type === value));
                    }}
                >
                    {availableEditorsOptions.map((option, index) => (
                        <Tab
                            aria-disabled={option.isDisabled}
                            disableFocusRipple
                            disableRipple
                            disableTouchRipple
                            key={index}
                            label={option.label.toLowerCase()}
                            value={option.value}
                            classes={{
                                selected: css({ outline: "none" }),
                                root: css({ "&:focus": { outline: "none" } }),
                                iconWrapper: css({
                                    marginLeft: "2px !important",
                                    display: "flex",
                                    alignItems: "center",
                                    pointerEvents: "auto",
                                }),
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
                            })}
                            iconPosition="end"
                            icon={
                                option.hint && (
                                    <div>
                                        <InfoTooltip text={option.hint} />
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
