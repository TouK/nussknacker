import React, { useState, ReactNode } from "react";
import { ExpressionObj } from "../expression/types";
import { Option } from "../../fragment-input-definition/TypeSelect";
import { Box, Tabs, Tab } from "@mui/material";
import { css } from "@emotion/css";
import { ParamType } from "../types";
import { editorsParameters } from "../expression/editorsParameters";
import { blendDarken, getBorderColor } from "../../../../../containers/theme/helpers";

interface Props {
    expressionObj: ExpressionObj;
    availableEditors: ParamType["editors"];
    onValueChange: (value: string | ExpressionObj) => void;
    children: ReactNode | ((selectedEditor: ParamType["editors"][number]) => ReactNode);
}

export const FieldSwitch = ({ availableEditors, onValueChange, expressionObj, children }: Props) => {
    const [selectedEditor, setSelectedEditor] = useState(
        availableEditors.find((editor) => {
            const editorParameters = editorsParameters[editor.type];
            return editorParameters.language === expressionObj.language;
        }) ?? availableEditors[0],
    );
    const availableEditorsOptions: Option[] = availableEditors.map((editor) => ({
        label: editorsParameters[editor.type].displayName,
        value: editor.type,
        isDisabled: false,
    }));

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
