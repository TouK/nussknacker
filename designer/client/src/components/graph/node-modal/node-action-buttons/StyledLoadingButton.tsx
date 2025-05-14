import { styled } from "@mui/material";

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
