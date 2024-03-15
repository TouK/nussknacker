import React from "react";
import TuneIcon from "@mui/icons-material/Tune";
import { StyledButtonWithFocus } from "../../../focusableStyled";
import { useTheme } from "@mui/material";

interface SettingsButton {
    isOpen: boolean;
    toggleIsOpen: () => void;
}

export default function SettingsButton({ isOpen, toggleIsOpen }: SettingsButton) {
    const theme = useTheme();

    return (
        <StyledButtonWithFocus
            style={{
                justifyContent: "center",
                alignItems: "center",
                display: "flex",
                marginRight: 5,
                backgroundColor: isOpen && theme.palette.action.focus,
            }}
            title={"Options"}
            onClick={toggleIsOpen}
        >
            <TuneIcon />
        </StyledButtonWithFocus>
    );
}
