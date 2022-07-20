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
    const accentMask = `acc${id}`;
    const mainMask = `m${id}`;
    const accentFilter = `p${id}`;
    const shapeFilter = `s${id}`;
    useLayoutEffect(() => {
        const isCached = document.getElementById(id) && process.env.NODE_ENV === "production";

        if (!isCached) {
            const svgDefs = (
                <SvgIcon sx={{ position: "absolute", top: -1000, opacity: 0 }}>
                    <defs>
                        <image id={id} width="100%" height="100%" preserveAspectRatio="xMidYMid slice" xlinkHref={iconSrc} />
                        <filter id={shapeFilter}>
                            <feColorMatrix
                                in="SourceGraphic"
                                result="shape"
                                type="matrix"
                                values="
                                    1 0 0 0 1
                                    0 1 0 0 1
                                    0 0 1 0 1
                                    0 0 0 1 0
                                "
                            />
                        </filter>
                        <filter id={accentFilter}>
                            <feColorMatrix type="luminanceToAlpha" />
                            <feColorMatrix
                                type="matrix"
                                values="
                                    1 0 0 0 1
                                    0 1 0 0 1
                                    0 0 1 0 1
                                    0 0 0 5 -.5
                                "
                            />
                        </filter>
                        <mask id={mainMask}>
                            <use x="0" y="0" width="100%" height="100%" xlinkHref={`#${id}`} filter={`url(#${shapeFilter})`} />
                        </mask>
                        <mask id={accentMask}>
                            <use
                                x="0"
                                y="0"
                                width="100%"
                                height="100%"
                                xlinkHref={`#${id}`}
                                filter={`url(#${accentFilter})`}
                                mask={`url(#${mainMask})`}
                            />
                        </mask>
                    </defs>
                </SvgIcon>
            );
            render(svgDefs, getOrCreateElement(`svg-preload-${id}`));
        }
    }, [iconSrc, id, accentFilter, accentMask, mainMask]);
    return [id, accentMask, mainMask];
}

export function NuIcon({ src, ...props }: SvgIconProps & { src: string }): JSX.Element {
    const [id, accentMask, mainMask] = useNuIconCache(src);
    return (
        <SvgIcon {...props}>
            {/*main icon part*/}
            <rect x="0" y="0" width="100%" height="100%" className="primary" mask={`url(#${mainMask})`} />
            {/*accented icon part original color*/}
            <use x="0" y="0" width="100%" height="100%" xlinkHref={`#${id}`} mask={`url(#${accentMask})`} />
            {/*accented icon part override with classname*/}
            <rect x="0" y="0" width="100%" height="100%" fill="none" className="secondary" mask={`url(#${accentMask})`} />
        </SvgIcon>
    );
}

export const MonoNuIcon = styled(NuIcon)({
    ".secondary": { fill: "currentColor" },
});
