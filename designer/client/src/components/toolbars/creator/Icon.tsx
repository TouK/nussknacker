import React from "react";

type IconProps = {
    className?: string;
    src?: string;
};

export function Icon({ className, src }: IconProps) {
    return (
        <svg className={className}>
            <use href={src} />
        </svg>
    );
}
