import { styled } from "@mui/material";
import { Icon } from "../../../toolbars/creator/Icon";

export const IconStyled = styled(Icon)(({ theme }) => ({
    width: 30,
    height: 30,
    color: theme.palette.common.white,
    "use, path": {
        transform: "scale(0.8)",
        transformOrigin: "16px 16px",
    },
}));

export const ModalHeader = styled("div")({
    flex: 1,
    display: "flex",
});
