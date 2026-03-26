import loadable from "@loadable/component";
import type { ComponentProps, ComponentType } from "react";
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

type DeferedString<T> = T | (string & NonNullable<unknown>);
type MarkdownProps = ComponentProps<typeof Markdown>;
type Components = MarkdownProps["components"];
const MarkdownWithCustomComponents = Markdown as ComponentType<
    Omit<MarkdownProps, "components"> & {
        components?: {
            [key in DeferedString<keyof Components>]?: key extends keyof Components
                ? ComponentType<ComponentProps<Components[key]>>
                : ComponentType;
        };
    }
>;

type MarkdownWithPluginsProps = ComponentProps<typeof MarkdownWithCustomComponents> & { linkTarget?: string };

export const MarkdownWithPlugins = ({
    remarkPlugins = [],
    children,
    components = {},
    linkTarget = "_blank",
    ...props
}: MarkdownWithPluginsProps) => {
    return (
        <MarkdownWithCustomComponents
            components={{
                [SANITIZED_PASSWORD_TAG_NAME]: PasswordMask,
                [ROUTER_LINK_TAG_NAME]: RouterLink,
                code: CodeBlock,
                ...components,
            }}
            remarkPlugins={[remarkDirective, remarkGfm, remarkDirectiveRehype, remarkHtml, ...remarkPlugins]}
            rehypePlugins={[[rehypeExternalLinks, { target: linkTarget, rel: ["noopener", "noreferrer"] }], rehypeRaw]}
            {...props}
        >
            {children}
        </MarkdownWithCustomComponents>
    );
};
