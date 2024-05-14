import { styled, Typography } from "@mui/material";
import React, { PropsWithChildren } from "react";
import Icon from "../../../../assets/img/documentation.svg";
import { MODAL_HEADER_HEIGHT } from "../../../../stylesheets/variables";
import { useEventTracking } from "../../../../containers/event-tracking";

const DocsLinkStyled = styled("a")(({ theme }) => ({
    color: `${theme.palette.text.secondary} !important`,
    height: `${MODAL_HEADER_HEIGHT}px`,
    display: "inline-block",
    textDecoration: "none !important",
    svg: {
        width: "14px",
        height: "14px",
        margin: theme.spacing(0, 0.5),
    },
}));

export const Docs = (props: PropsWithChildren<{ docsUrl: string; style?: React.CSSProperties }>) => {
    const { children, docsUrl, style } = props;
    const { trackEvent } = useEventTracking();

    return (
        <DocsLinkStyled
            target="_blank"
            href={docsUrl}
            title="Documentation"
            rel="noreferrer"
            onClick={() => {
                trackEvent({ type: "CLICK_NODE_DOCUMENTATION" });
            }}
        >
            <div style={style}>
                {children && (
                    <Typography mx={0.5} variant={"subtitle2"}>
                        {children}
                    </Typography>
                )}
                <Icon />
            </div>
        </DocsLinkStyled>
    );
};
