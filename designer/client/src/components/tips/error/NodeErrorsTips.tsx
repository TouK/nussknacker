import React from "react";
import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import i18next from "i18next";
import { concat, difference, isEmpty } from "lodash";

export const NodeErrorsTips = ({ propertiesErrors, nodeErrors, showDetails, currentProcess }) => {
    const nodeIds = Object.keys(nodeErrors);

    const errorsOnTopPresent = (otherNodeErrorIds, propertiesErrors) => {
        return !isEmpty(otherNodeErrorIds) || !isEmpty(propertiesErrors);
    };

    const looseNodeIds = nodeIds.filter((nodeId) => nodeErrors[nodeId].some((error) => error.typ === "LooseNode"));
    const invalidEndNodeIds = nodeIds.filter((nodeId) => nodeErrors[nodeId].some((error) => error.typ === "InvalidTailOfBranch"));
    const otherNodeErrorIds = difference(nodeIds, concat(looseNodeIds, invalidEndNodeIds));
    const errorsOnTop = errorsOnTopPresent(otherNodeErrorIds, propertiesErrors);

    return (
        <>
            <NodeErrorsLinkSection
                nodeIds={concat(otherNodeErrorIds, isEmpty(propertiesErrors) ? [] : "properties")}
                message={i18next.t("errors.errorsIn", "Errors in: ")}
                showDetails={showDetails}
                currentProcess={currentProcess}
            />
            <NodeErrorsLinkSection
                nodeIds={looseNodeIds}
                message={i18next.t("errors.looseNodes", "Loose nodes: ")}
                showDetails={showDetails}
                currentProcess={currentProcess}
                errorsOnTop={errorsOnTop}
            />
            <NodeErrorsLinkSection
                nodeIds={invalidEndNodeIds}
                message={i18next.t("errors.invalidScenarioEnd", "Scenario must end with a sink, processor or fragment: ")}
                showDetails={showDetails}
                currentProcess={currentProcess}
                errorsOnTop={errorsOnTop}
            />
        </>
    );
};
