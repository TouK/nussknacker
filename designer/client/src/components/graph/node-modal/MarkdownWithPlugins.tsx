import type { PropsOf } from "@emotion/react";
import loadable from "@loadable/component";
import type { Component } from "hast-util-to-jsx-runtime/lib/types";
import type { ComponentProps, JSXElementConstructor } from "react";
import React from "react";
import Markdown from "react-markdown";
import rehypeExternalLinks from "rehype-external-links";
import rehypeRaw from "rehype-raw";
import remarkDirective from "remark-directive";
import remarkDirectiveRehype from "remark-directive-rehype";
import remarkGfm from "remark-gfm";
import remarkHtml from "remark-html";

import { ROUTER_LINK_TAG_NAME, SANITIZED_PASSWORD_TAG_NAME } from "./customTagNames";

const PasswordMask = loadable(() => import("./PasswordMask"));
const RouterLink = loadable(() => import("./RouterLink"));
const CodeBlock = loadable(() => import("../../../common/CodeBlock"));

type MarkdownWithPluginsProps = PropsOf<typeof Markdown> & { linkTarget?: string };
type ForceComponent<T extends JSXElementConstructor<unknown>> = Component<ComponentProps<T>>;

export const MarkdownWithPlugins = ({
    remarkPlugins = [],
    children,
    components = {},
    linkTarget = "_blank",
    ...props
}: MarkdownWithPluginsProps) => {
    return (
        <Markdown
            components={{
                [SANITIZED_PASSWORD_TAG_NAME]: PasswordMask as ForceComponent<typeof PasswordMask>,
                [ROUTER_LINK_TAG_NAME]: RouterLink as ForceComponent<typeof RouterLink>,
                code: CodeBlock as ForceComponent<typeof CodeBlock>,
                ...components,
            }}
            remarkPlugins={[remarkDirective, remarkGfm, remarkDirectiveRehype, remarkHtml, ...remarkPlugins]}
            rehypePlugins={[[rehypeExternalLinks, { target: linkTarget, rel: ["noopener", "noreferrer"] }], rehypeRaw]}
            {...props}
        >
            {children}
        </Markdown>
    );
};
