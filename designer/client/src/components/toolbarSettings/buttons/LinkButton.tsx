import React from "react";
import FallbackIcon from "../../../assets/img/toolbarButtons/link.svg";
import { PlainStyleLink } from "../../../containers/plainStyleLink";
import { ToolbarButton } from "../../toolbarComponents/toolbarButtons";
import UrlIcon from "../../UrlIcon";

export interface LinkButtonProps {
    name: string;
    title?: string;
    url: string;
    icon?: string;
    disabled?: boolean;
}

export function LinkButton({ url, icon, name, title, disabled }: LinkButtonProps): JSX.Element {
    return (
        <PlainStyleLink disabled={disabled} to={url} tabIndex={-1}>
            <ToolbarButton
                name={name}
                title={title || name}
                disabled={disabled}
                icon={<UrlIcon src={icon} FallbackComponent={FallbackIcon} />}
            />
        </PlainStyleLink>
    );
}
