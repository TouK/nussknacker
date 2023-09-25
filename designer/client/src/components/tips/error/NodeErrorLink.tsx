import React, { MouseEventHandler } from "react";
import { Link } from "react-router-dom";
import { css, cx } from "@emotion/css";
import { NodeId } from "../../../types";
import { useNkTheme } from "../../../containers/theme";
import Color from "color";

export const NodeErrorLink = (props: { onClick: MouseEventHandler<HTMLAnchorElement>; nodeId: NodeId; disabled?: boolean }) => {
    const { onClick, nodeId, disabled } = props;
    const { theme } = useNkTheme();

    const styles = css({
        whiteSpace: "normal",
        fontWeight: 600,
        color: theme.colors.error,
        "a&": {
            "&:hover": {
                color: Color(theme.colors.error).lighten(0.25).hex(),
            },
            "&:focus": {
                color: theme.colors.error,
                textDecoration: "none",
            },
        },
    });

    return disabled ? (
        <span
            className={cx(
                styles,
                css({
                    color: Color(theme.colors.error).desaturate(0.5).lighten(0.1).hex(),
                }),
            )}
        >
            {nodeId}
        </span>
    ) : (
        <Link className={styles} to={`?nodeId=${nodeId}`} onClick={onClick}>
            {nodeId}
        </Link>
    );
};
