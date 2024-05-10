import { styled } from "@mui/material";
import { Icon } from "../../../toolbars/creator/Icon";
import { WindowKind } from "../../../../windowManager";

export const WindowHeaderIconStyled = styled(Icon)<{ type?: WindowKind }>(({ theme, type }) => {
    return {
        ...theme.palette.custom.getWindowStyles(type),
        width: 30,
        height: 30,
        "use, path": {
            transform: "scale(0.8)",
            transformOrigin: "16px 16px",
        },
    };
});

export const ModalHeader = styled("div")({
    flex: 1,
    display: "flex",
});
