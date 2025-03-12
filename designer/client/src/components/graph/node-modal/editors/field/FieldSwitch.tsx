import React, { useState, ReactNode } from "react";
import { editorParameters, editors } from "../expression/Editor";
import { ExpressionObj } from "../expression/types";
import { Option } from "../../fragment-input-definition/TypeSelect";
import { Box, Tabs, Tab } from "@mui/material";
import { css } from "@emotion/css";
import { ParamType } from "../types";

interface Props {
    expressionObj: ExpressionObj;
    availableEditors: ParamType["editors"];
    onValueChange: (value: string | ExpressionObj) => void;
    children: ReactNode | ((selectedEditor: ParamType["editors"][number]) => ReactNode);
}

export const FieldSwitch = ({ availableEditors, onValueChange, expressionObj, children }: Props) => {
    const [selectedEditor, setSelectedEditor] = useState(
        availableEditors.find((editor) => {
            const selectedEditor = editors[editor.type];
            return selectedEditor.language === expressionObj.language;
        }) ?? availableEditors[0],
    );
    const availableEditorsOptions: Option[] = availableEditors.map((editor) => ({
        label: editorParameters[editor.type].displayName,
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
                    sx={{ minHeight: "20px", minWidth: "45px" }}
                    TabIndicatorProps={{ sx: { display: "none" } }}
                    onChange={(_, value: string) => {
                        const selectedEditor = availableEditors.find((editor) => editor.type === value);
                        const editor = editors[selectedEditor.type];
                        onValueChange({ ...expressionObj, language: editor.language });
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
                                root: css({ outline: "none", "&:focus": { outline: "none" } }),
                            }}
                            sx={(theme) => ({
                                fontSize: "0.65rem",
                                padding: theme.spacing(0, 0.5),
                                textTransform: "none",
                                minHeight: "20px",
                                minWidth: "45px",
                            })}
                        />
                    ))}
                </Tabs>
            </Box>
            {typeof children === "function" ? children(selectedEditor) : children}
        </Box>
    );
};
