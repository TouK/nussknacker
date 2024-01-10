import React from "react";
import i18next from "i18next";
import { concat, difference, isEmpty } from "lodash";
import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import { NodeType, ScenarioGraph, NodeValidationError } from "../../../types";
import { Process } from "src/components/Process/types";

interface NodeErrorsTips {
    propertiesErrors: NodeValidationError[];
    nodeErrors: Record<string, NodeValidationError[]>;
    showDetails: (event: React.SyntheticEvent<Element, Event>, details: NodeType) => void;
    currentProcess: Process;
}

export const NodeErrorsTips = ({ propertiesErrors, nodeErrors, showDetails, currentProcess }: NodeErrorsTips) => {
    const nodeIds = Object.keys(nodeErrors);

    const errorsOnTopPresent = (otherNodeErrorIds: string[], propertiesErrors: NodeValidationError[]) => {
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
