import { List, ListItemButton, ListItemText, styled } from "@mui/material";
import i18next from "i18next";
import React from "react";

interface Props {
    value: string;
    options: string[];
    optionLabels?: string[];
    onValueChange: (val: string) => void;
}

const StyledList = styled(List)(() => ({
    width: "100%",
    height: "100%",
    border: "none",
    outline: "none",
    background: "transparent",
    font: "inherit",
    padding: 0,
    margin: 0,
    overflowY: "auto",
}));

export const Dropdown: React.FC<Props> = ({ value, options, optionLabels, onValueChange }) => {
    return (
        <StyledList tabIndex={0} role="listbox" dense disablePadding>
            {options.map((option, index) => (
                <ListItemButton
                    key={option}
                    role="option"
                    aria-selected={option === value}
                    selected={option === value}
                    onClick={() => {
                        onValueChange(option);
                    }}
                >
                    <ListItemText primaryTypographyProps={{ noWrap: true }} primary={optionLabels?.[index] ?? option} />
                </ListItemButton>
            ))}
            {!options.length && (
                <ListItemButton disabled sx={{ py: 0.25, px: 0.5 }}>
                    <ListItemText
                        primaryTypographyProps={{ fontStyle: "italic", noWrap: true, sx: { opacity: 0.7 } }}
                        primary={i18next.t("testingDataRecords.dropdown.noOptions", "No options")}
                    />
                </ListItemButton>
            )}
        </StyledList>
    );
};
