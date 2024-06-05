import React from "react";
import { useTranslation } from "react-i18next";
import DefaultIcon from "../../../../assets/img/toolbarButtons/custom_action.svg";
import { CustomAction } from "../../../../types";
import { useWindows, WindowKind } from "../../../../windowManager";
import { StatusType } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import UrlIcon from "../../../UrlIcon";

type CustomActionProps = {
    action: CustomAction;
    processName: string;
} & ToolbarButtonProps;

export default function CustomActionButton(props: CustomActionProps) {
    const { action, disabled, type } = props;

    const { t } = useTranslation();

    const icon = action.icon ? <UrlIcon src={action.icon} FallbackComponent={DefaultIcon} /> : <DefaultIcon />;

    const toolTip = disabled ? t("panels.actions.custom-action.tooltips.disabled", "Disabled for current state.") : null;

    const { open } = useWindows();
    return (
        <ToolbarButton
            name={action.name}
            title={toolTip}
            disabled={disabled}
            icon={icon}
            onClick={() =>
                open<CustomAction>({
                    title: action.name,
                    kind: WindowKind.customAction,
                    meta: action,
                })
            }
            type={type}
        />
    );
}
