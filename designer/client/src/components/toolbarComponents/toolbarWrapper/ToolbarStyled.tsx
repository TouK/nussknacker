import { styled } from "@mui/material";
import CollapseIcon from "../../../assets/img/arrows/panel-hide-arrow.svg";
import CloseIcon from "../../../assets/img/close.svg";

export const Title = styled("div")({
    padding: "0 .25em",
    overflow: "hidden",
    textOverflow: "ellipsis",
    flex: 1,
});

export const IconWrapper = styled("div")({
    padding: 0,
    flexShrink: 0,
    border: 0,
    background: "none",
    display: "flex",
    alignItems: "center",
});

export const StyledCollapseIcon = styled(CollapseIcon, {
    shouldForwardProp: (name) => name !== "collapsed",
})<{ collapsed?: boolean }>(({ collapsed, theme }) => ({
    padding: "0 .25em",
    height: "1em",
    transition: theme.transitions.create("transform", { duration: theme.transitions.duration.standard }),
    transform: `rotate(${collapsed ? 180 : 90}deg)`,
}));

export const StyledCloseIcon = styled(CloseIcon)({
    height: "1em",
    width: "1em",
});
