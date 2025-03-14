import { styled } from "@mui/material";
import { getBorderColor } from "../../../../../../containers/theme/helpers";
import { nodeInputWithError } from "../../../NodeDetailsContent/NodeTableStyled";

export const TimeRangeStyled = styled("div")(({ theme }) => ({
    minWidth: "60%",
    flex: 1,
    display: "inline-block",
    width: "100%",
    "& .time-range-component": {
        display: "inline-flex",
        lineHeight: "inherit",
        height: "22px",
    },
    "& .time-range-component:after": {
        clear: "both",
    },
    "& .time-range-input": {
        width: "30px !important",
        height: "100%",
        outline: "none !important",
        backgroundColor: theme.palette.background.paper,
        textAlign: "center",
        padding: 0,
        border: 0,
        borderBottom: `1px solid ${theme.palette.common.white}`,
        "&:focus, &:focus-within": {
            borderBottom: `1px solid ${theme.palette.primary.main}`,
        },
        [`&.${nodeInputWithError}`]: {
            borderBottom: `1px solid ${theme.palette.error.light} !important`,
            borderOffset: "initial !important",
            borderRadius: 2,
        },
        "&.read-only": {
            borderBottom: 0,
        },
    },
    "& .time-range-components": {
        display: "flex",
        flexWrap: "wrap",
        height: "35px",
        gap: theme.spacing(1),
        outline: `1px solid ${getBorderColor(theme)} !important`,
        paddingLeft: theme.spacing(1),
        alignItems: "center",
    },
    "& .time-range-component-label": {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "0 0.6em 0 0.6em",
        fontWeight: 700,
        lineHeight: 1,
        textAlign: "center",
        verticalAlign: "baseline",
        borderRadius: "0.25em",
    },
}));
