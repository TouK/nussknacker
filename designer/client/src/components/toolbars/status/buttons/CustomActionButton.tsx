import React, {ComponentType, useEffect, useState} from "react";
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
import {useSelector} from "react-redux";
import {getProcessVersionId} from "../../../../reducers/selectors/graph";
import {resolveCustomActionDisplayability} from "../../../../helpers/customActionDisplayabilityResolver";

type CustomActionProps = {
    action: CustomAction;
    processName: string;
    processStatus: StatusType | null;
} & ToolbarButtonProps;

export default function CustomActionButton(props: CustomActionProps) {
    const { action, processStatus, disabled } = props;
    const [isAvailable, setIsAvailable] = useState<boolean>(false);

    const { t } = useTranslation();

    const icon = action.icon ? (
        <UrlIcon src={action.icon} FallbackComponent={DefaultIcon as ComponentType<FallbackProps>} />
    ) : (
        <DefaultIcon />
    );

  useEffect(() => {
    const resolveDisplayability = async () => {
      const res = await resolveCustomActionDisplayability(action.displayPolicy);
      setIsAvailable(!disabled &&
        action.allowedStateStatusNames.includes(statusName) && res);
    }
    resolveDisplayability();
  }, []);

    const statusName = processStatus?.name;

    const toolTip = isAvailable
        ? null
        : t("panels.actions.custom-action.tooltips.disabled", "Disabled for {{statusName}} status.", { statusName });

    const { open } = useWindows();
    return (
        <ToolbarButton
            name={action.name}
            title={toolTip}
            disabled={!isAvailable}
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
