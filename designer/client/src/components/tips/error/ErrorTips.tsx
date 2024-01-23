import React, { useMemo } from "react";
import { concat, isEmpty } from "lodash";
import { Props } from "./Errors";
import { NodeValidationError } from "../../../types";
import { v4 as uuid4 } from "uuid";
import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import i18next from "i18next";

export const ErrorTips = ({ errors, showDetails, scenario }: Props) => {
    const { globalErrors, processPropertiesErrors, invalidNodes } = errors;

    const invalidNodeIds = Object.keys(invalidNodes);

    const renderGlobalErrorSimpleMessage = (error: NodeValidationError) => (
        <span key={uuid4()} title={error.description}>
            {error.message + (error.fieldName ? `(${error.fieldName})` : "")}
        </span>
    );

    const globalErrorsLinkSections = useMemo(
        () =>
            globalErrors.map((error, index) =>
                isEmpty(error.nodeIds) ? (
                    renderGlobalErrorSimpleMessage(error.error)
                ) : (
                    <NodeErrorsLinkSection
                        key={index}
                        nodeIds={error.nodeIds}
                        message={`${error.error.message}: `}
                        showDetails={showDetails}
                        scenario={scenario}
                    />
                ),
            ),
        [globalErrors, showDetails, scenario],
    );

    return isEmpty(invalidNodes) && isEmpty(processPropertiesErrors) && isEmpty(globalErrors) ? null : (
        <div>
            {globalErrorsLinkSections}
            <NodeErrorsLinkSection
                nodeIds={concat(invalidNodeIds, isEmpty(processPropertiesErrors) ? [] : "properties")}
                message={i18next.t("errors.errorsIn", "Errors in: ")}
                showDetails={showDetails}
                scenario={scenario}
            />
        </div>
    );
};
