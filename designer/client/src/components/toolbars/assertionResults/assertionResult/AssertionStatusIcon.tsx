import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import React from "react";

interface Props {
    isSuccess: boolean;
}

export const AssertionStatusIcon = ({ isSuccess }: Props) => {
    const Icon = isSuccess ? CheckCircleIcon : ErrorOutlineIcon;
    const color = isSuccess ? "success" : "error";

    return <Icon color={color} sx={{ fontSize: "16px" }} />;
};
