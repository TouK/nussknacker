import React, { MouseEventHandler } from "react";
import { NavLink } from "react-router-dom";
import { css, cx } from "@emotion/css";
import { NodeId } from "../../../types";
import Color from "color";
import { useTheme } from "@mui/material";
import { ErrorLinkStyle } from "./styled";

export const NodeErrorLink = (props: { onClick: MouseEventHandler<HTMLAnchorElement>; nodeId: NodeId; disabled?: boolean }) => {
    const { onClick, nodeId, disabled } = props;
    const theme = useTheme();

    return disabled ? (
        <ErrorLinkStyle
            variant={"body2"}
            component={"span"}
            className={cx(
                css({
                    color: Color(theme.palette.error.main).desaturate(0.5).lighten(0.1).hex(),
                }),
            )}
        >
            {nodeId}
        </ErrorLinkStyle>
    ) : (
        <ErrorLinkStyle variant={"body2"} component={NavLink} to={`?nodeId=${nodeId}`} onClick={onClick}>
            {/* blank values don't render as links so this is a workaround */}
            {nodeId.trim() === "" ? "blank" : nodeId}
        </ErrorLinkStyle>
    );
};
