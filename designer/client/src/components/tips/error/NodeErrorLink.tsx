import React, { MouseEventHandler } from "react";
import { NavLink } from "react-router-dom";
import { css, cx } from "@emotion/css";
import { NodeId } from "../../../types";
import Color from "color";
import { Typography, useTheme } from "@mui/material";

export const NodeErrorLink = (props: { onClick: MouseEventHandler<HTMLAnchorElement>; nodeId: NodeId; disabled?: boolean }) => {
    const { onClick, nodeId, disabled } = props;
    const theme = useTheme();

    const styles = css({
        whiteSpace: "normal",
        fontWeight: 600,
        color: theme.palette.error.light,
        "a&": {
            "&:hover": {
                color: Color(theme.palette.error.main).lighten(0.25).hex(),
            },
            "&:focus": {
                color: theme.palette.error.main,
                textDecoration: "none",
            },
        },
    });

    return disabled ? (
        <Typography
            variant={"body2"}
            component={"span"}
            className={cx(
                styles,
                css({
                    color: Color(theme.palette.error.main).desaturate(0.5).lighten(0.1).hex(),
                }),
            )}
        >
            {nodeId}
        </Typography>
    ) : (
        <Typography variant={"body2"} component={NavLink} className={styles} to={`?nodeId=${nodeId}`} onClick={onClick}>
            {/* blank values don't render as links so this is a workaround */}
            {nodeId.trim() === "" ? "blank" : nodeId}
        </Typography>
    );
};
