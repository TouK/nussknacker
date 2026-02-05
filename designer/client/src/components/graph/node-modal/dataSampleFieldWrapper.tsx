import React from "react";
import { useTranslation } from "react-i18next";

import { useUserSettings } from "../../../common/useUserSettings";
import { getIsRunning } from "../../../reducers/selectors/scenarioState";
import { useAppSelector } from "../../../store/storeHelpers";
import type { ExpressionLang } from "./editors/expression/types";
import { useAceEditorRangeMessages } from "./editors/expression/useAceEditorRangeMessages";
import { FieldAddons } from "./fieldAddons";
import { SendRequestButton } from "./node-action-buttons/SendRequestButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function DataSampleFieldWrapper({ children, node, parameter, isEditMode, showValidation, fieldErrors }: FieldWrapperProps) {
    const { t } = useTranslation();
    const isRunning = useAppSelector(getIsRunning);
    const [showSendRequestButton, showLines] = useUserSettings(
        "node.showSendRequestButton",
        `editor.${parameter.expression.language as ExpressionLang}.showLines`,
    );

    const { hasRangeText } = useAceEditorRangeMessages(fieldErrors, showLines);

    if (!showSendRequestButton) return <>{children}</>;
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
