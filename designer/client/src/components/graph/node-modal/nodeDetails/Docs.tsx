import React, { PropsWithChildren } from "react";
import { styled } from "@mui/material";
import Icon from "../../../../assets/img/documentation.svg";
import { variables } from "../../../../stylesheets/variables";

const DocsLinkStyled = styled("a")`
    color: #ffffff !important;
    height: ${variables.modalHeaderHeight}px;
    display: inline-block;
    svg {
        width: 14px;
        height: 14px;
    }
`;

export const Docs = (props: PropsWithChildren<{ docsUrl: string }>) => {
    const { children, docsUrl } = props;
    return (
        <DocsLinkStyled target="_blank" href={docsUrl} title="Documentation" rel="noreferrer">
            <div>
                {children && <span style={{ paddingRight: 5 }}>{children}</span>}
                <Icon />
            </div>
        </DocsLinkStyled>
    );
};
