import { styled, Typography } from "@mui/material";
import React, { PropsWithChildren } from "react";
import Icon from "../../../../assets/img/documentation.svg";
import { MODAL_HEADER_HEIGHT } from "../../../../stylesheets/variables";

const DocsLinkStyled = styled("a")`
    color: #ffffff !important;
    height: ${MODAL_HEADER_HEIGHT}px;
    display: inline-block;
    text-decoration: none !important;
    svg {
        width: 14px;
        height: 14px;
        margin: 0 3px;
    }
`;

export const Docs = (props: PropsWithChildren<{ docsUrl: string; style?: React.CSSProperties }>) => {
    const { children, docsUrl, style } = props;
    return (
        <DocsLinkStyled target="_blank" href={docsUrl} title="Documentation" rel="noreferrer">
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
