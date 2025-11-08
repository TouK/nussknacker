import { Box } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { getIsRunning } from "../../../reducers/selectors/scenarioState";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../store/storeHelpers";
import { getValidationErrorsForField } from "./editors/Validators";
import { SendRequestButton } from "./node-action-buttons/SendRequestButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function DataSampleFieldWrapper({ children, node, parameter, errors }: FieldWrapperProps) {
    const { t } = useTranslation();
    const isRunning = useAppSelector(getIsRunning);
    const settings = useAppSelector(getUserSettings);

    if (!settings["node.showSendRequestButton"]) return <>{children}</>;

    return (
        <>
            {children}
            {
                <Box display={"flex"} justifyContent={"flex-end"}>
                    <SendRequestButton
                        disabled={!isRunning ? true : getValidationErrorsForField(errors, parameter.name).length > 0}
                        infoTooltip={
                            !isRunning ? t("node.actions.sendRequest.tooltip.deployScenarioFirst", "Deploy your scenario first") : null
                        }
                        expression={parameter.expression.expression}
                        node={node}
                    />
                </Box>
            }
        </>
    );
}
