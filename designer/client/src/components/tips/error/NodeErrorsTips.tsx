import React from "react";
import i18next from "i18next";
import { concat, difference, isEmpty } from "lodash";
import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import { NodeType, NodeValidationError, GlobalValidationError } from "../../../types";
import { v4 as uuid4 } from "uuid";
import { Scenario } from "src/components/Process/types";

interface NodeErrorsTips {
    propertiesErrors: NodeValidationError[];
    nodeErrors: Record<string, NodeValidationError[]>;
    globalErrors: GlobalValidationError[];
    showDetails: (event: React.SyntheticEvent<Element, Event>, details: NodeType) => void;
    scenario: Scenario;
}

// TODO local: clean this up
export const NodeErrorsTips = ({ propertiesErrors, nodeErrors, globalErrors, showDetails, scenario }: NodeErrorsTips) => {
    const nodeIds = Object.keys(nodeErrors);

    const errorsOnTopPresent = (otherNodeErrorIds: string[], propertiesErrors: NodeValidationError[]) => {
        return !isEmpty(otherNodeErrorIds) || !isEmpty(propertiesErrors);
    };

    const looseNodeIds = nodeIds.filter((nodeId) => nodeErrors[nodeId].some((error) => error.typ === "LooseNode"));
    const invalidEndNodeIds = nodeIds.filter((nodeId) => nodeErrors[nodeId].some((error) => error.typ === "InvalidTailOfBranch"));
    const otherNodeErrorIds = difference(nodeIds, concat(looseNodeIds, invalidEndNodeIds));
    const errorsOnTop = errorsOnTopPresent(otherNodeErrorIds, propertiesErrors);

    const globalError = (error: NodeValidationError) => (
        <span key={uuid4()} title={error.description}>
            {error.message + (error.fieldName ? `(${error.fieldName})` : "")}
        </span>
    );

    const globalErrorsLinkSections = globalErrors.map((e, index) =>
        isEmpty(e.nodeIds) ? (
            globalError(e.error)
        ) : (
            <NodeErrorsLinkSection
                key={index}
                nodeIds={e.nodeIds}
                message={`${e.error.message}: `}
                showDetails={showDetails}
                scenario={scenario}
            />
        ),
    );

    return (
        <>
            {globalErrorsLinkSections}
            <NodeErrorsLinkSection
                nodeIds={concat(otherNodeErrorIds, isEmpty(propertiesErrors) ? [] : "properties")}
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
        </>
    );
};
