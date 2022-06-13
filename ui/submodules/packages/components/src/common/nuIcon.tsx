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
    const iconSrc = getComponentIconSrc(src);
    const id = useMemo(() => `x${new Chance(iconSrc).hash({ length: 10 })}`, [iconSrc]);
    const primaryMaskId = "pm" + id;
    const secondaryMaskId = "sm" + id;

    useLayoutEffect(() => {
        function reverseValues(tableValues: any[]) {
            return tableValues.map((x) => 1 - x);
        }

        if (!document.getElementById(id) || process.env.NODE_ENV !== "production") {
            const primary = "p" + id;
            const secondary = "s" + id;
            const tableValues = [0, 0.5, 1, 0.75, 0.5, 0, ...Array(24).fill(0)];
            const mainPartAlphaTransfer = tableValues.join(" ");
            const accentTableValues = reverseValues(tableValues);
            accentTableValues[0] = 0; // remove background after reverse
            const accentPartAlphaTransfer = accentTableValues.join(" ");
            const svgDefs = (
                <SvgIcon sx={{ position: "absolute", top: -1000, opacity: 0 }}>
                    <defs>
                        <image id={id} width="100%" height="100%" preserveAspectRatio="xMidYMid slice" xlinkHref={iconSrc} />
                        <filter id={primary}>
                            <feGaussianBlur in="SourceGraphic" stdDeviation=".1" />
                            <feMerge>
                                <feMergeNode />
                                <feMergeNode in="SourceGraphic" />
                            </feMerge>
                            <feColorMatrix type="luminanceToAlpha" />
                            <feComponentTransfer>
                                <feFuncA type="discrete" tableValues={mainPartAlphaTransfer} />
                                <feFuncR type="discrete" tableValues="1" />
                                <feFuncG type="discrete" tableValues="1" />
                                <feFuncB type="discrete" tableValues="1" />
                            </feComponentTransfer>
                        </filter>
                        <filter id={secondary}>
                            <feColorMatrix type="luminanceToAlpha" />
                            <feComponentTransfer>
                                <feFuncA type="discrete" tableValues={accentPartAlphaTransfer} />
                                <feFuncR type="discrete" tableValues="1" />
                                <feFuncG type="discrete" tableValues="1" />
                                <feFuncB type="discrete" tableValues="1" />
                            </feComponentTransfer>
                        </filter>
                        <mask id={primaryMaskId}>
                            <use x="0" y="0" width="100%" height="100%" xlinkHref={`#${id}`} filter={`url(#${primary})`} />
                        </mask>
                        <mask id={secondaryMaskId}>
                            <use x="0" y="0" width="100%" height="100%" xlinkHref={`#${id}`} filter={`url(#${secondary})`} />
                        </mask>
                    </defs>
                </SvgIcon>
            );
            render(svgDefs, getOrCreateElement(`svg-preload-${id}`));
        }
    }, [iconSrc, id, primaryMaskId, secondaryMaskId]);
    return [id, primaryMaskId, secondaryMaskId];
}

export function NuIcon({ src, ...props }: SvgIconProps & { src: string }): JSX.Element {
    const [id, primaryMaskId, secondaryMaskId] = useNuIconCache(src);
    return (
        <SvgIcon {...props}>
            {/*main icon part*/}
            <rect x="0" y="0" width="100%" height="100%" className="primary" mask={`url(#${primaryMaskId})`} />
            {/*accented icon part original color*/}
            <use x="0" y="0" width="100%" height="100%" xlinkHref={`#${id}`} mask={`url(#${secondaryMaskId})`} />
            {/*accented icon part override with classname*/}
            <rect x="0" y="0" width="100%" height="100%" fill="none" className="secondary" mask={`url(#${secondaryMaskId})`} />
        </SvgIcon>
    );
}

export const MonoNuIcon = styled(NuIcon)({
    ".secondary": { fill: "currentColor" },
});
