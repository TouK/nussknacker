import { PropsOf } from "@emotion/react";
import { lighten, styled } from "@mui/material";
import React, { PropsWithChildren } from "react";
import Markdown from "react-markdown";
import { Link } from "react-router-dom";
import rehypeExternalLinks from "rehype-external-links";
import rehypeRaw from "rehype-raw";
import remarkDirective from "remark-directive";
import remarkDirectiveRehype from "remark-directive-rehype";
import remarkHtml from "remark-html";
import { getBorderColor } from "../../../containers/theme/helpers";

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

type MarkdownWithPluginsProps = PropsOf<typeof Markdown> & { linkTarget?: string };
const MarkdownWithPlugins = ({
    remarkPlugins = [],
    children,
    components = {},
    linkTarget = "_blank",
    ...props
}: MarkdownWithPluginsProps) => (
    <Markdown
        components={{
            "router-link": RouterLink,
            ...components,
        }}
        remarkPlugins={[remarkDirective, remarkDirectiveRehype, remarkHtml, ...remarkPlugins]}
        rehypePlugins={[[rehypeExternalLinks, { target: linkTarget, rel: ["noopener", "noreferrer"] }], rehypeRaw]}
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
