import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import { Box, IconButton, styled, Tooltip } from "@mui/material";
import React from "react";

import { LoadingButton } from "../../../../windowManager/LoadingButton";

export const StyledLoadingButton = styled(LoadingButton)(({ theme }) => ({
    fontSize: "12px",
    textTransform: "inherit",
    padding: theme.spacing(0.5, 1),
    margin: 0,
    ":not(:last-child)": {
        marginRight: 0,
    },
}));

export function BuilderIconButton({ onClick, label = "Builder" }: { onClick: () => void; label?: string }): React.JSX.Element {
    const title = (
        <Box>
            <Box sx={{ fontWeight: "bold", mb: 0.5 }}>Open {label}</Box>
            <Box>Build a SpEL expression visually or write it manually. Use # to access variables and helpers.</Box>
        </Box>
    );
    return (
        <Tooltip title={title}>
            <IconButton onClick={onClick} color="primary" sx={{ padding: 0 }}>
                <AutoFixHighIcon sx={{ width: "1rem", height: "1rem" }} />
            </IconButton>
        </Tooltip>
    );
}
