import React, { ComponentType } from "react";
import FallbackIcon from "../../../assets/img/toolbarButtons/link.svg";
import { PlainStyleLink } from "../../../containers/plainStyleLink";
import { ToolbarButton } from "../../toolbarComponents/toolbarButtons";
import UrlIcon from "../../UrlIcon";
import { FallbackProps } from "react-error-boundary";
import { BuiltinButtonTypes } from "./BuiltinButtonTypes";
import { CustomButtonTypes } from "./CustomButtonTypes";

export interface LinkButtonProps {
    name: string;
    type?: CustomButtonTypes;
    title?: string;
    url: string;
    icon?: string;
    disabled?: boolean;
}

export function LinkButton({ url, icon, name, title, disabled, type }: LinkButtonProps): JSX.Element {
    return (
        <PlainStyleLink disabled={disabled} to={url} tabIndex={-1}>
            <ToolbarButton
                name={name}
                type={type}
                title={title || name}
                disabled={disabled}
                icon={<UrlIcon src={icon} FallbackComponent={FallbackIcon as ComponentType<FallbackProps>} />}
            />
        </PlainStyleLink>
    );
}
