import React, { useMemo } from "react";
import { concat, difference, isEmpty } from "lodash";
import { Props } from "./Errors";
import { NodeValidationError } from "../../../types";
import { v4 as uuid4 } from "uuid";
import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import i18next from "i18next";

export const ErrorTips = ({ errors, showDetails, scenario }: Props) => {
    const { globalErrors, processPropertiesErrors, invalidNodes } = errors;

    const nodeIds = Object.keys(invalidNodes);

    const errorsOnTopPresent = (otherNodeErrorIds: string[], propertiesErrors: NodeValidationError[]) => {
        return !isEmpty(otherNodeErrorIds) || !isEmpty(propertiesErrors);
    };

    // TODO: after BE passes LooseNode and InvalidTailOfBranch errors correctly as global errors remove this
    const looseNodeIds = nodeIds.filter((nodeId) => invalidNodes[nodeId].some((error) => error.typ === "LooseNode"));
    const invalidEndNodeIds = nodeIds.filter((nodeId) => invalidNodes[nodeId].some((error) => error.typ === "InvalidTailOfBranch"));
    const otherNodeErrorIds = difference(nodeIds, concat(looseNodeIds, invalidEndNodeIds));
    const errorsOnTop = errorsOnTopPresent(otherNodeErrorIds, processPropertiesErrors);

    // TODO local: is this fieldName in global error message ever used? remove if not
    const renderGlobalErrorSimpleMessage = (error) => (
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
                nodeIds={concat(otherNodeErrorIds, isEmpty(processPropertiesErrors) ? [] : "properties")}
                message={i18next.t("errors.errorsIn", "Errors in: ")}
                showDetails={showDetails}
                scenario={scenario}
            />
            <NodeErrorsLinkSection
                nodeIds={looseNodeIds}
                message={i18next.t("errors.looseNodes", "Loose nodes: ")}
                showDetails={showDetails}
                scenario={scenario}
                errorsOnTop={errorsOnTop}
            />
            <NodeErrorsLinkSection
                nodeIds={invalidEndNodeIds}
                message={i18next.t("errors.invalidScenarioEnd", "Scenario must end with a sink, processor or fragment: ")}
                showDetails={showDetails}
                scenario={scenario}
                errorsOnTop={errorsOnTop}
            />
        </div>
    );
};
