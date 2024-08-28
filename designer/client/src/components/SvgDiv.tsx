import React, { ComponentType, DetailedHTMLProps, HTMLAttributes } from "react";
import loadable from "@loadable/component";
import { ErrorBoundary } from "react-error-boundary";
import { styled } from "@mui/material";
import { absoluteBePath } from "../common/UrlUtils";

const absoluteExp = /^(?<root>(?<proto>(https?:)?\/)?\/)?.*\.svg$/i;

const AsyncSvg = loadable.lib(
    async ({ src }: { src: string }) => {
        const match = src?.match(absoluteExp);

        if (!match) {
            throw `${src} is not svg path`;
        }

        if (match.groups.root) {
            const response = await fetch(match.groups.proto ? src : absoluteBePath(src));
            const html = await response.text();
            if (!html.trim().endsWith("</svg>")) {
                throw "response text is not valid svg";
            }
            return html;
        }

        // assume not absolute paths as local webpack paths
        const module = await import(`!raw-loader!../assets/img/${src}`);
        return module.default;
    },
    {
        cacheKey: ({ src }) => src,
    },
);

const Flex = styled("div")({
    display: "flex",
    width: "100%",
    height: "100%",
});

export interface InlineSvgProps extends DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
    src: string;
    FallbackComponent?: ComponentType<{
        className?: string;
    }>;
}

export const InlineSvg = ({ FallbackComponent, src, id, ...rest }: InlineSvgProps): JSX.Element => (
    <AsyncSvg src={src}>
        {(__html) => <Flex {...rest} dangerouslySetInnerHTML={{ __html: id ? __html.replace("<svg ", `<svg id="${id}"`) : __html }} />}
    </AsyncSvg>
);
