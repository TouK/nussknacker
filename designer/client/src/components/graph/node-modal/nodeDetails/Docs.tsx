import React, { PropsWithChildren } from "react";
import { styled } from "@mui/material";
import Icon from "../../../../assets/img/documentation.svg";
import { variables } from "../../../../stylesheets/variables";

const DocsLinkStyled = styled("a")`
    color: #ffffff !important;
    height: ${variables.modalHeaderHeight}px;
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
                {children && <span>{children}</span>}
                <Icon />
            </div>
        </DocsLinkStyled>
    );
};
