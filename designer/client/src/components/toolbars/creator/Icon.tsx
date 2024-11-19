import React from "react";
import PlaceholderIcon from "../../../components/common/error-boundary/images/placeholder-icon.svg";

type IconProps = {
    className?: string;
    src?: string;
};

export function Icon({ className, src }: IconProps) {
    if (!src) {
        return <PlaceholderIcon className={className} />;
    }

    return (
        <svg className={className}>
            <use href={src} />
        </svg>
    );
}
