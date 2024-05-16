import PropertiesSvg from "../../../assets/img/properties.svg";
import React from "react";

type IconProps = {
    className?: string;
    src?: string;
};

export function Icon({ className, src }: IconProps) {
    if (!src) {
        return <PropertiesSvg className={className} />;
    }

    return (
        <svg className={className}>
            <use href={src} />
        </svg>
    );
}
