import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/properties.svg";
import { hasError, hasPropertiesErrors } from "../../../../reducers/selectors/graph2";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { ToolbarButtonProps } from "../../types";

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
    const propertiesErrors = useAppSelector(hasPropertiesErrors);
    const errors = useAppSelector(hasError);

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
