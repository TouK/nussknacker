import { styled, SvgIcon, SvgIconProps } from "@mui/material";
import Chance from "chance";
import React, { useContext, useLayoutEffect, useMemo } from "react";
import { render } from "react-dom";
import { NkIconsContext } from "../settings/nkApiProvider";

function createAndAppendDiv(id: string) {
    const element = document.createElement("div");
    element.id = id;
    document.body.appendChild(element);
    return element;
}

function getOrCreateElement(id: string) {
    return document.getElementById(id) || createAndAppendDiv(id);
}

function useNuIconCache(src: string) {
    const { getComponentIconSrc } = useContext(NkIconsContext);
    const id = useMemo(() => `x${new Chance(src).hash({ length: 10 })}`, [src]);
    const primary = useMemo(() => "p" + id, [id]);
    const primaryMaskId = useMemo(() => "pm" + id, [id]);
    const secondaryMaskId = useMemo(() => "sm" + id, [id]);
    const secondary = useMemo(() => "s" + id, [id]);
    useLayoutEffect(() => {
        const isLoaded = !!document.getElementById(id);
        if (!isLoaded) {
            render(
                <SvgIcon sx={{ position: "absolute", top: -1000, opacity: 0 }}>
                    <defs>
                        <image
                            id={id}
                            width="100%"
                            height="100%"
                            preserveAspectRatio="xMidYMid slice"
                            xlinkHref={getComponentIconSrc(src)}
                        />
                        <filter id={primary}>
                            <feImage x="0" y="0" width="100%" height="100%" preserveAspectRatio="xMidYMid slice" xlinkHref={`#${id}`} />
                            <feColorMatrix type="luminanceToAlpha" />
                            <feComponentTransfer>
                                <feFuncA type="discrete" tableValues="0 1 1 1 0 0 0 0 0 0 0 0 0 0 0 0 " />
                                <feFuncR type="discrete" tableValues="1" />
                                <feFuncG type="discrete" tableValues="1" />
                                <feFuncB type="discrete" tableValues="1" />
                            </feComponentTransfer>
                        </filter>
                        <filter id={secondary}>
                            <feImage x="0" y="0" width="100%" height="100%" preserveAspectRatio="xMidYMid slice" xlinkHref={`#${id}`} />
                            <feColorMatrix type="luminanceToAlpha" />
                            <feComponentTransfer>
                                <feFuncA type="discrete" tableValues="0 0 .5 1 1 1" />
                                <feFuncR type="discrete" tableValues="1" />
                                <feFuncG type="discrete" tableValues="1" />
                                <feFuncB type="discrete" tableValues="1" />
                            </feComponentTransfer>
                        </filter>
                        <mask id={primaryMaskId}>
                            <use xlinkHref={`#${id}`} style={{ filter: `url(#${primary})` }} />
                        </mask>
                        <mask id={secondaryMaskId}>
                            <use xlinkHref={`#${id}`} style={{ filter: `url(#${secondary})` }} />
                        </mask>
                    </defs>
                </SvgIcon>,
                getOrCreateElement(`svg-preload-${id}`),
            );
        }
    }, [getComponentIconSrc, id, primary, primaryMaskId, secondary, secondaryMaskId, src]);
    return [id, primaryMaskId, secondaryMaskId];
}

export function NuIcon({ src, ...props }: SvgIconProps & { src: string }): JSX.Element {
    const [id, primaryMaskId, secondaryMaskId] = useNuIconCache(src);
    return (
        <SvgIcon {...props}>
            <use x="0" y="0" width="100%" height="100%" xlinkHref={`#${id}`} />
            <rect x="0" y="0" width="100%" height="100%" className="primary" mask={`url(#${primaryMaskId})`} />
            <rect x="0" y="0" width="100%" height="100%" fill="none" className="secondary" mask={`url(#${secondaryMaskId})`} />
        </SvgIcon>
    );
}

export const MonoNuIcon = styled(NuIcon)({
    ".secondary": { fill: "currentColor" },
});
