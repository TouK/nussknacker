import React from "react";
import { useTranslation } from "react-i18next";
import DefaultIcon from "../../../../assets/img/toolbarButtons/custom_action.svg";
import { CustomAction } from "../../../../types";
import { useWindows, WindowKind } from "../../../../windowManager";
import { StatusType } from "../../../Process/types";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import UrlIcon from "../../../UrlIcon";
import { ACTION_DIALOG_WIDTH } from "../../../toolbarSettings/actions";

type CustomActionProps = {
    action: CustomAction;
    processName: string;
    processStatus: StatusType | null;
} & ToolbarButtonProps;

export default function CustomActionButton(props: CustomActionProps) {
    const { action, processStatus, disabled, type } = props;

    const { t } = useTranslation();

    const icon = action.icon ? <UrlIcon src={action.icon} FallbackComponent={DefaultIcon} /> : <DefaultIcon />;

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
                    width: ACTION_DIALOG_WIDTH,
                    meta: action,
                })
            }
            type={type}
        />
    );
}
