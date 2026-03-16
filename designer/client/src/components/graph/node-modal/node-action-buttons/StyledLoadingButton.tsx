import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import { IconButton, styled, Tooltip } from "@mui/material";
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

export function BuilderIconButton({ onClick }: { onClick: () => void }): React.JSX.Element {
    return (
        <Tooltip title="Builder">
            <IconButton size="small" onClick={onClick} color="primary">
                <AutoFixHighIcon fontSize="small" />
            </IconButton>
        </Tooltip>
    );
}
