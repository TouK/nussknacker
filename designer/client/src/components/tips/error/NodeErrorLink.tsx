import { css, cx } from "@emotion/css";
import { useTheme } from "@mui/material";
import Color from "color";
import type { MouseEventHandler } from "react";
import React from "react";
import { NavLink } from "react-router-dom";

import type { NodeId } from "../../../types/node";
import { ErrorLinkStyle } from "./styled";

export const NodeErrorLink = (props: {
    onClick: MouseEventHandler<HTMLAnchorElement>;
    nodeId: NodeId;
    nodeName: string;
    disabled?: boolean;
}) => {
    const { onClick, nodeId, nodeName, disabled } = props;
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
            {nodeName}
        </ErrorLinkStyle>
    ) : (
        <ErrorLinkStyle variant={"body2"} component={NavLink} to={`?nodeId=${nodeId}`} onClick={onClick}>
            {/* blank values don't render as links so this is a workaround */}
            {nodeName.trim() === "" ? "blank" : nodeName}
        </ErrorLinkStyle>
    );
};
