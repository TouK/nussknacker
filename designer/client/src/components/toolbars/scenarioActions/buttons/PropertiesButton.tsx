import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/properties.svg";
import { getProcessUnsavedNewName, hasError, hasPropertiesErrors, getScenario } from "../../../../reducers/selectors/graph";
import { useWindows } from "../../../../windowManager";
import NodeUtils from "../../../graph/NodeUtils";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";

function PropertiesButton(props: ToolbarButtonProps): JSX.Element {
    const { t } = useTranslation();
    const { openNodeWindow } = useWindows();
    const { disabled, type } = props;
    const scenario = useSelector(getScenario);
    const name = useSelector(getProcessUnsavedNewName);
    const propertiesErrors = useSelector(hasPropertiesErrors);
    const errors = useSelector(hasError);

    const processProperties = useMemo(() => NodeUtils.getProcessPropertiesNode(scenario, name), [name, scenario]);

    const onClick = useCallback(() => openNodeWindow(processProperties, scenario), [openNodeWindow, processProperties, scenario]);

    return (
        <ToolbarButton
            name={t("panels.actions.edit-properties.button", "properties")}
            hasError={errors && propertiesErrors}
            icon={<Icon />}
            disabled={disabled}
            onClick={onClick}
            type={type}
        />
    );
}

export default PropertiesButton;
