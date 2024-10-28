import React, { ComponentType, DetailedHTMLProps, ImgHTMLAttributes, useEffect, useState } from "react";
import { absoluteBePath } from "../common/UrlUtils";
import { InlineSvg, InlineSvgProps } from "./SvgDiv";
import { PlaceholderIconFallbackComponent } from "./common/error-boundary/fallbackComponent/PlaceholderIconFallbackComponent";

export interface ImageWithFallbackProps extends DetailedHTMLProps<ImgHTMLAttributes<HTMLImageElement>, HTMLImageElement> {
    src: string;
    FallbackComponent?: ComponentType;
}

function ImageWithFallback({ src, FallbackComponent, ...props }: ImageWithFallbackProps): JSX.Element {
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

export default function UrlIcon({ FallbackComponent = PlaceholderIconFallbackComponent, ...props }: UrlIconProps): JSX.Element {
    switch (true) {
        case /\.svg$/i.test(props.src):
            return <InlineSvg {...props} FallbackComponent={FallbackComponent} />;
        default:
            return <ImageWithFallback {...props} FallbackComponent={FallbackComponent} />;
    }
}
