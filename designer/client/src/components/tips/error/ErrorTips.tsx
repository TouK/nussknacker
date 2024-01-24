import React, { useMemo } from "react";
import { concat, isEmpty } from "lodash";
import { Props } from "./Errors";
import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import i18next from "i18next";

export const ErrorTips = ({ errors, showDetails, scenario }: Props) => {
    const { globalErrors, processPropertiesErrors, invalidNodes } = errors;

    const invalidNodeIds = Object.keys(invalidNodes);

    const globalErrorsLinkSections = useMemo(
        () =>
            globalErrors.map((error, index) =>
                isEmpty(error.nodeIds) ? (
                    <span key={index} title={error.error.description}>
                        {error.error.message}
                    </span>
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
