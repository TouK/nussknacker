import PassedIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Dangerous";
import NoAssertionsIcon from "@mui/icons-material/RemoveCircleOutline";
import type { Theme } from "@mui/material";
import { CircularProgress } from "@mui/material";
import React from "react";

export type AssertionStatus = "success" | "error" | "noAssertions" | "loading";

interface Props {
    status: AssertionStatus;
    variant?: "main" | "dark" | "light";
}

const LoadingIcon = ({ sx }: { sx: React.ComponentProps<typeof CircularProgress>["sx"] }) => <CircularProgress size={16} sx={sx} />;

const STATUS_CONFIG: Record<AssertionStatus, { Icon: React.ElementType; getColor: (theme: Theme, variant: Props["variant"]) => string }> = {
    success: { Icon: PassedIcon, getColor: (theme, variant) => theme.palette.success[variant] },
    error: { Icon: ErrorIcon, getColor: (theme, variant) => theme.palette.error[variant] },
    noAssertions: {
        Icon: NoAssertionsIcon,
        getColor: (theme, variant) => (variant === "dark" ? theme.palette.primary.contrastText : theme.palette.common.white),
    },
    loading: {
        Icon: LoadingIcon,
        getColor: (theme, variant) => theme.palette.primary[variant],
    },
};

export const AssertionStatusIcon = ({ status, variant = "main" }: Props) => {
    const { Icon, getColor } = STATUS_CONFIG[status];
    return <Icon sx={(theme: Theme) => ({ fontSize: "16px", color: getColor(theme, variant) })} />;
};
