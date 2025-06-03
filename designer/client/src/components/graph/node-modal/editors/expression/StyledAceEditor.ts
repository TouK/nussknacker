import { Box, styled } from "@mui/material";

import { getBorderColor } from "../../../../../containers/theme/helpers";

export const StyledAceEditor = styled(Box)(({ theme }) => ({
    "& .ace_tooltip": {
        ...theme.typography.body2,
        padding: theme.spacing(1),
        background: `${theme.palette.background.paper}`,
        borderColor: getBorderColor(theme),
        borderRadius: "6px",
        transform: "translate(-110%, -50%)",
        minWidth: "400px",
    },
    "& .ace-error-marker": {
        position: "absolute",
        borderBottom: `2px solid ${theme.palette.error.main}`,
        borderRadius: 0,
    },
}));
