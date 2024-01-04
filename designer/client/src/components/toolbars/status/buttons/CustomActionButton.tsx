import React, { ComponentType } from "react";
import { useTranslation } from "react-i18next";
import DefaultIcon from "../../../../assets/img/toolbarButtons/custom_action.svg";
import { CustomAction } from "../../../../types";
import { useWindows } from "../../../../windowManager";
import { WindowKind } from "../../../../windowManager/WindowKind";
import { StatusType } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import UrlIcon from "../../../UrlIcon";
import { FallbackProps } from "react-error-boundary";

type CustomActionProps = {
    action: CustomAction;
    processName: string;
    processStatus: StatusType | null;
} & ToolbarButtonProps;

export default function CustomActionButton(props: CustomActionProps) {
    const { action, processStatus, disabled } = props;

    const { t } = useTranslation();

    const icon = action.icon ? (
        <UrlIcon src={action.icon} FallbackComponent={DefaultIcon as ComponentType<FallbackProps>} />
    ) : (
        <DefaultIcon />
    );

    const statusName = processStatus?.name;
    const available = !disabled && action.allowedStateStatusNames.includes(statusName);

    const toolTip = available
        ? null
        : t("panels.actions.custom-action.tooltips.disabled", "Disabled for {{statusName}} status.", { statusName });

    const { open } = useWindows();
    return (
        <ToolbarButton
            name={action.name}
            title={toolTip}
            disabled={!available}
            icon={icon}
            onClick={() =>
                open<CustomAction>({
                    title: action.name,
                    kind: WindowKind.customAction,
                    meta: action,
                })
            }
        />
    );
}
