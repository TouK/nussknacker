import { ToggleButton, ToggleButtonGroup, styled } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

export type TestCaseMode = "results" | "definitions";

interface Props {
    value: TestCaseMode;
    onChange: (value: TestCaseMode) => void;
}

const StyledToggleButtonGroup = styled(ToggleButtonGroup)(({ theme }) => ({
    display: "flex",
    padding: theme.spacing(0.5, 1, 1),
}));

const StyledToggleButton = styled(ToggleButton)(({ theme }) => ({
    flex: 1,
    padding: theme.spacing(0.25, 1),
    fontSize: theme.typography.caption.fontSize,
    fontWeight: theme.typography.caption.fontWeight,
    textTransform: "none",
    lineHeight: 1.75,
}));

export const TestCaseSwitchMode = ({ value, onChange }: Props) => {
    const { t } = useTranslation();

    const handleChange = (_: React.MouseEvent<HTMLElement>, newValue: TestCaseMode | null) => {
        if (newValue !== null) {
            onChange(newValue);
        }
    };

    return (
        <StyledToggleButtonGroup value={value} exclusive onChange={handleChange} size="small" fullWidth aria-label="test case view mode">
            <StyledToggleButton value="results" aria-label={t("testCases.switchMode.results", "Results")}>
                {t("testCases.switchMode.results", "Results")}
            </StyledToggleButton>
            <StyledToggleButton value="definitions" aria-label={t("testCases.switchMode.definitions", "Definitions")}>
                {t("testCases.switchMode.definitions", "Definitions")}
            </StyledToggleButton>
        </StyledToggleButtonGroup>
    );
};
