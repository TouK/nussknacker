import React from "react";
import { useTranslation } from "react-i18next";

import { getIsRunning } from "../../../reducers/selectors/scenarioState";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../store/storeHelpers";
import { useAceEditorRangeMessages } from "./editors/expression/useAceEditorRangeMessages";
import { FieldAddons } from "./fieldAddons";
import { SendRequestButton } from "./node-action-buttons/SendRequestButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function DataSampleFieldWrapper({ children, node, parameter, isEditMode, showValidation, fieldErrors }: FieldWrapperProps) {
    const { t } = useTranslation();
    const isRunning = useAppSelector(getIsRunning);
    const settings = useAppSelector(getUserSettings);
    const showLines = Boolean(settings[`editor.${parameter.expression.language}.showLines`]);

    const { hasRangeText } = useAceEditorRangeMessages(fieldErrors, showLines);

    if (!settings["node.showSendRequestButton"]) return <>{children}</>;
    if (!isEditMode) return <>{children}</>;

    return (
        <>
            {children}
            <FieldAddons hasError={showValidation && fieldErrors.length > 0 && !hasRangeText}>
                <SendRequestButton
                    disabled={!isRunning ? true : fieldErrors.length > 0}
                    infoTooltip={
                        !isRunning ? t("node.actions.sendRequest.tooltip.deployScenarioFirst", "Deploy your scenario first") : null
                    }
                    node={node}
                />
            </FieldAddons>
        </>
    );
}
