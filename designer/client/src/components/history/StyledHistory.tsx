import { styled } from "@mui/material";
import { VersionType } from "./HistoryItem";
import Badge from "../deployed.svg";
import { blendDarken, blendLighten } from "../../containers/theme/helpers";

export const HistoryItemStyled = styled("li")<{ type: VersionType }>(({ theme, type }) => ({
    cursor: "pointer",
    overflow: "hidden",
    position: "relative",
    padding: "5px 0 5px 42px",
    display: "flex",
    justifyContent: "space-between",
    alignItems: "flex-start",
    ".date": {
        pointerEvents: "none",
    },
    "&::before": {
        content: "''",
        position: "absolute",
        left: "20px",
        top: 0,
        width: "20px",
        height: "999px",
        border: `1px solid ${theme.palette.primary.main}`,
        borderWidth: "0px 0 0 1px",
        paddingLeft: "10px",
        "&:last-of-type::before": {
            height: "14px",
        },
    },
    "&:first-of-type::before": {
        top: "14px",
    },
    "&:last-of-type::before": {
        height: "14px",
    },
    "&::after": {
        content: "''",
        position: "absolute",
        left: "13px",
        top: "14px",
        width: "16px",
        height: "16px",
        background: type === VersionType.current ? theme.palette.primary.main : theme.palette.background.paper,
        border: `2px solid ${theme.palette.primary.main}`,
        borderRadius: "50%",
        paddingLeft: "10px",
    },
    "&:hover::after": {
        backgroundColor: type === VersionType.current ? blendLighten(theme.palette.primary.main, 0.05) : "none",
    },
    "&:hover": {
        backgroundColor: theme.palette.action.hover,
        boxSizing: "border-box",
        "&::after": {
            backgroundColor: blendDarken(theme.palette.primary.main, 0.2),
        },
    },
}));

export const TrackVertical = styled("div")`
    width: 8px !important;
    position: absolute;
    right: 2px;
    bottom: 2px;
    top: 2px;
    border-radius: 6px !important;
    visibility: visible;
`;

export const ProcessHistoryWrapper = styled("ul")`
    font-size: 12px;
    padding: 5px 0;
    list-style: none;
    margin: 0;
`;

export const StyledBadge = styled(Badge)(({ theme }) => ({
    color: theme.palette.getContrastText(theme.palette.primary.main),
    height: "1.2em",
    margin: "0 1.2em",
    "path:first-of-type": {
        fill: theme.palette.primary.main,
    },
}));
