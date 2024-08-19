import { PropsOf } from "@emotion/react";
import Markdown from "react-markdown";
import remarkDirective from "remark-directive";
import remarkDirectiveRehype from "remark-directive-rehype";
import React, { PropsWithChildren } from "react";
import { lighten, styled } from "@mui/material";
import { getBorderColor } from "../../../containers/theme/helpers";
import { Link } from "react-router-dom";

const RouterLink = ({
    to,
    children,
}: PropsWithChildren<{
    to: string;
}>) => <Link to={to}>{children}</Link>;

declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace JSX {
        interface IntrinsicElements {
            "router-link": PropsOf<typeof RouterLink>;
        }
    }
}

const MarkdownWithPlugins = ({
    remarkPlugins = [],
    children,
    components = {},
    linkTarget = "_blank",
    ...props
}: PropsOf<typeof Markdown>) => (
    <Markdown
        linkTarget={linkTarget}
        components={{
            "router-link": RouterLink,
            ...components,
        }}
        remarkPlugins={[remarkDirective, remarkDirectiveRehype, ...remarkPlugins]}
        {...props}
    >
        {children}
    </Markdown>
);

export const MarkdownStyled = styled(MarkdownWithPlugins)(({ theme }) => ({
    ...theme.typography.body2,
    marginTop: theme.spacing(1.5),
    marginBottom: theme.spacing(1.25),
    table: {
        backgroundColor: theme.palette.background.paper,
        marginTop: theme.spacing(0.5),
        marginBottom: theme.spacing(0.5),
        width: "95%",
    },
    "th, td": {
        padding: theme.spacing(1.25),
        border: `1px solid ${getBorderColor(theme)}`,
        fontSize: "12px",
    },
    a: {
        color: theme.palette.primary.main,
        "&:hover": {
            color: lighten(theme.palette.primary.main, 0.25),
        },
        "&:focus": {
            color: theme.palette.primary.main,
            textDecoration: "none",
        },
    },
}));

export type MarkdownStyledProps = PropsOf<typeof MarkdownStyled>;
