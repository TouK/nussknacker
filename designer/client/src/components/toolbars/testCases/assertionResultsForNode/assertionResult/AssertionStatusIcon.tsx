import PassedIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Dangerous";
import NoAssertionsIcon from "@mui/icons-material/RemoveCircleOutline";
import type { Theme } from "@mui/material";
import React from "react";

export type AssertionStatus = "success" | "error" | "noAssertions";

interface Props {
    status: AssertionStatus;
    variant?: "main" | "dark" | "light";
}

const STATUS_CONFIG = {
    success: { Icon: PassedIcon, getColor: (theme: Theme, variant: Props["variant"]) => theme.palette.success[variant] },
    error: { Icon: ErrorIcon, getColor: (theme: Theme, variant: Props["variant"]) => theme.palette.error[variant] },
    noAssertions: {
        Icon: NoAssertionsIcon,
        getColor: (theme: Theme, variant: Props["variant"]) =>
            variant === "dark" ? theme.palette.primary.contrastText : theme.palette.common.white,
    },
} satisfies Record<AssertionStatus, unknown>;

export const AssertionStatusIcon = ({ status, variant = "main" }: Props) => {
    const { Icon, getColor } = STATUS_CONFIG[status];

    return <Icon sx={(theme) => ({ fontSize: "16px", color: getColor(theme, variant) })} />;
};
