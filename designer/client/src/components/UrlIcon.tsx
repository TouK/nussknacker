import type { ComponentType, DetailedHTMLProps, ImgHTMLAttributes } from "react";
import React, { useEffect, useState } from "react";

import { absoluteBePath } from "../common/UrlUtils";
import { PlaceholderIconFallbackComponent } from "./common/error-boundary/fallbackComponent/PlaceholderIconFallbackComponent";
import type { InlineSvgProps } from "./SvgDiv";
import { InlineSvg } from "./SvgDiv";

export interface ImageWithFallbackProps extends DetailedHTMLProps<ImgHTMLAttributes<HTMLImageElement>, HTMLImageElement> {
    src: string;
    FallbackComponent?: ComponentType;
}

function ImageWithFallback({ src, FallbackComponent, ...props }: ImageWithFallbackProps): React.JSX.Element {
    const [error, setError] = useState(() => !src);

    useEffect(() => {
        setError(!src);
    }, [src]);

    if (error && FallbackComponent) {
        return <FallbackComponent {...props} />;
    }

    return <img onError={() => setError(true)} src={src && absoluteBePath(src)} {...props} />;
}

export type UrlIconProps = InlineSvgProps & ImageWithFallbackProps;

export default function UrlIcon({ FallbackComponent = PlaceholderIconFallbackComponent, ...props }: UrlIconProps): React.JSX.Element {
    switch (true) {
        case /\.svg$/i.test(props.src):
            return <InlineSvg {...props} FallbackComponent={FallbackComponent} />;
        default:
            return <ImageWithFallback {...props} FallbackComponent={FallbackComponent} />;
    }
}
