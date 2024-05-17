import { styled } from "@mui/material";
import { PropsOf } from "@emotion/react";
import Icon from "../../../../assets/img/documentation.svg";
import React from "react";
import { Subtype } from "./Subtype";
import { EventTrackingSelector, EventTrackingType, getEventTrackingProps } from "../../../../containers/event-tracking";

const LinkStyled = styled("a")({
    display: "flex",
    "&, &:hover": {
        color: "inherit",
        textDecoration: "inherit",
    },
});

const DocsIcon = styled(Icon)({
    width: 14,
    height: 14,
});

type DocsProps = PropsOf<typeof Subtype> & {
    href: string;
};

export const Docs = ({ href, ...props }: DocsProps) => (
    <LinkStyled
        target="_blank"
        href={href}
        title="Documentation"
        rel="noreferrer"
        {...getEventTrackingProps({ selector: EventTrackingSelector.NodeDocumentation, event: EventTrackingType.CLICK })}
    >
        <Subtype endIcon={<DocsIcon />} {...props} />
    </LinkStyled>
);
