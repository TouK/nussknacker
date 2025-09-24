import type { PropsOf } from "@emotion/react";
import loadable from "@loadable/component";
import React from "react";
import Markdown from "react-markdown";
import rehypeExternalLinks from "rehype-external-links";
import rehypeRaw from "rehype-raw";
import remarkDirective from "remark-directive";
import remarkDirectiveRehype from "remark-directive-rehype";
import remarkHtml from "remark-html";

import { ROUTER_LINK_TAG_NAME, SANITIZED_PASSWORD_TAG_NAME } from "./customTagNames";

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
            [SANITIZED_PASSWORD_TAG_NAME]: loadable(() => import("./PasswordMask")),
            [ROUTER_LINK_TAG_NAME]: loadable(() => import("./RouterLink")),
            code: loadable(() => import("../../../common/CodeBlock")),
            ...components,
        }}
        remarkPlugins={[remarkDirective, remarkDirectiveRehype, remarkHtml, ...remarkPlugins]}
        rehypePlugins={[[rehypeExternalLinks, { target: linkTarget, rel: ["noopener", "noreferrer"] }], rehypeRaw]}
        {...props}
    >
        {children}
    </Markdown>
);
