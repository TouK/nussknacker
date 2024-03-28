import { css, cx } from "@emotion/css";
import React from "react";
import customAttrs from "../assets/json/nodeAttributes.json";
import { NodeType } from "../types";
import { BORDER_RADIUS, CONTENT_PADDING, iconBackgroundSize, iconSize, portSize, RECT_HEIGHT, RECT_WIDTH } from "./graph/EspNode/esp";
import NodeUtils from "./graph/NodeUtils";
import { ComponentIcon } from "./toolbars/creator/ComponentIcon";
import { alpha, styled, useTheme } from "@mui/material";
import { blendLighten } from "../containers/theme/nuTheme";
import { blend } from "@mui/system";

export function ComponentPreview({ node, isActive, isOver }: { node: NodeType; isActive?: boolean; isOver?: boolean }): JSX.Element {
    const theme = useTheme();

    const nodeStyles = css({
        position: "relative",
        width: RECT_WIDTH,
        height: RECT_HEIGHT,
        borderRadius: BORDER_RADIUS,
        boxSizing: "content-box",
        display: "inline-flex",
        filter: `drop-shadow(0 4px 8px ${alpha(theme.palette.common.black, 0.5)})`,
        borderWidth: 0.5,
        borderStyle: "solid",
        transformOrigin: "80% 50%",
        transform: `translate(-80%, -50%) scale(${isOver ? 1 : 0.9}) rotate(${isActive ? (isOver ? -2 : 2) : 0}deg) scale(${
            isActive ? 1 : 1.5
        })`,
        opacity: isActive ? undefined : 0,
        transition: "all .5s, opacity .3s",
        willChange: "transform, opacity, border-color, background-color",
    });

    const nodeColors = css({
        opacity: 0.5,
        borderColor: blendLighten(theme.palette.background.paper, 0.25),
        backgroundColor: blendLighten(theme.palette.background.paper, 0.04),
    });
    const nodeColorsHover = css({
        borderColor: blend(blendLighten(theme.palette.background.paper, 0.25), theme.palette.secondary.main, 0.2),
        backgroundColor: blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.secondary.main, 0.2),
    });

    const imageStyles = css({
        padding: iconSize / 2,
        borderBottomLeftRadius: BORDER_RADIUS - 2,
        borderTopLeftRadius: BORDER_RADIUS - 2,
        "> svg": {
            height: iconSize,
            width: iconSize,
        },
    });

    const imageColors = css({
        background: customAttrs[node?.type]?.styles.fill,
        color: theme.palette.common.white,
    });

    const ContentStyled = styled("div")(({ theme }) => ({
        color: theme.palette.text.primary,
        fontSize: theme.typography.caption.fontSize,
        flex: 1,
        whiteSpace: "nowrap",
        display: "flex",
        alignItems: "center",
        overflow: "hidden",
        span: {
            margin: CONTENT_PADDING,
            flex: 1,
            textOverflow: "ellipsis",
            overflow: "hidden",
        },
    }));

    const colors = isOver ? nodeColorsHover : nodeColors;
    return (
        <div className={cx(colors, nodeStyles)}>
            <div className={cx(imageStyles, imageColors)}>
                <ComponentIcon node={node} />
            </div>
            <ContentStyled>
                <span>{node?.id}</span>
                {NodeUtils.hasInputs(node) && <Port className={cx(css({ top: 0, transform: "translateY(-50%)" }), colors)} />}
                {NodeUtils.hasOutputs(node) && <Port className={cx(css({ bottom: 0, transform: "translateY(50%)" }), colors)} />}
            </ContentStyled>
        </div>
    );
}

const Port = ({ className }: { className?: string }) => {
    const size = portSize * 2;
    const position = size / 2;
    const port = css({
        width: size,
        height: size,
        borderRadius: size,
        boxSizing: "border-box",
        borderWidth: 0.5,
        borderStyle: "solid",
        position: "absolute",
        bottom: -position,
        right: iconBackgroundSize - position,
    });
    return <div className={cx(port, className)} />;
};
