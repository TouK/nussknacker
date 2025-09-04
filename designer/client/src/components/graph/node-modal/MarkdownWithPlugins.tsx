import type { PropsOf } from "@emotion/react";
import type { PropsWithChildren } from "react";
import React from "react";
import Markdown from "react-markdown";
import { Link } from "react-router-dom";
import rehypeExternalLinks from "rehype-external-links";
import rehypeRaw from "rehype-raw";
import remarkDirective from "remark-directive";
import remarkDirectiveRehype from "remark-directive-rehype";
import remarkHtml from "remark-html";

import { CodeBlock } from "../../../common/CodeBlock";
import { PasswordMask } from "./PasswordMask";
import { SANITIZED_PASSWORD_TAG_NAME } from "./tagName";

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
            [SANITIZED_PASSWORD_TAG_NAME]: PropsOf<typeof PasswordMask>;
        }
    }
}

type MarkdownWithPluginsProps = PropsOf<typeof Markdown> & { linkTarget?: string };
export const MarkdownWithPlugins = ({
    remarkPlugins = [],
    children,
    components = {},
    linkTarget = "_blank",
    ...props
}: MarkdownWithPluginsProps) => (
    <Markdown
        components={{
            [SANITIZED_PASSWORD_TAG_NAME]: PasswordMask,
            "router-link": RouterLink,
            code: CodeBlock,
            ...components,
        }}
        remarkPlugins={[remarkDirective, remarkDirectiveRehype, remarkHtml, ...remarkPlugins]}
        rehypePlugins={[[rehypeExternalLinks, { target: linkTarget, rel: ["noopener", "noreferrer"] }], rehypeRaw]}
        {...props}
    >
        {children}
    </Markdown>
);
