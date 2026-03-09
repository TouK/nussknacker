import PassedIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Dangerous";
import React from "react";

interface Props {
    isSuccess: boolean;
    variant?: "main" | "dark" | "light";
}

export const AssertionStatusIcon = ({ isSuccess, variant = "main" }: Props) => {
    const Icon = isSuccess ? PassedIcon : ErrorIcon;

    return (
        <Icon sx={(theme) => ({ fontSize: "16px", color: isSuccess ? theme.palette.success[variant] : theme.palette.error[variant] })} />
    );
};
