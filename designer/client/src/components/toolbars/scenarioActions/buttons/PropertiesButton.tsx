import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/properties.svg";
import { hasError, hasPropertiesErrors } from "../../../../reducers/selectors/graph";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";

export function useOpenProperties() {
    const { open } = useWindows();
    return useCallback(
        () =>
            open({
                kind: WindowKind.editProperties,
                isResizable: true,
                shouldCloseOnEsc: false,
            }),
        [open],
    );
}

function PropertiesButton(props: ToolbarButtonProps): JSX.Element {
    const { t } = useTranslation();
    const { disabled, type } = props;
    const propertiesErrors = useSelector(hasPropertiesErrors);
    const errors = useSelector(hasError);

    const openProperties = useOpenProperties();

    return (
        <ToolbarButton
            name={t("panels.actions.edit-properties.button", "properties")}
            hasError={errors && propertiesErrors}
            icon={<Icon />}
            disabled={disabled}
            onClick={() => openProperties()}
            type={type}
        />
    );
}

export default PropertiesButton;
