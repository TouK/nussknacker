import React from "react";
import { useTranslation } from "react-i18next";

import { getIsRunning } from "../../../reducers/selectors/scenarioState";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../store/storeHelpers";
import { getValidationErrorsForField } from "./editors/Validators";
import { FieldAddons } from "./fieldAddons";
import { SendRequestButton } from "./node-action-buttons/SendRequestButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function DataSampleFieldWrapper({ children, node, parameter, errors, isEditMode, showValidation }: FieldWrapperProps) {
    const { t } = useTranslation();
    const isRunning = useAppSelector(getIsRunning);
    const settings = useAppSelector(getUserSettings);

    if (!settings["node.showSendRequestButton"]) return <>{children}</>;
    if (!isEditMode) return <>{children}</>;

    const fieldErrors = getValidationErrorsForField(errors, parameter.name);
    return (
        <>
            {children}
            <FieldAddons hasError={showValidation && fieldErrors.length > 0}>
                <SendRequestButton
                    disabled={!isRunning ? true : fieldErrors.length > 0}
                    infoTooltip={
                        !isRunning ? t("node.actions.sendRequest.tooltip.deployScenarioFirst", "Deploy your scenario first") : null
                    }
                    expression={parameter.expression.expression}
                    node={node}
                />
            </FieldAddons>
        </>
    );
}
