import { produce } from "immer";
import { isEqual } from "lodash";

import { findParameters } from "../../components/graph/node-modal/NodeDetailsContent/helpers";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import type { NodeDetailsState } from "../nodeDetailsState";

function filterScenarioData(mode: "save" | "execution", nodesDetails?: NodeDetailsState) {
    return produce((draft: ScenarioGraph) => {
        draft.nodes?.forEach((node) => {
            /**
             * It's a fix of https://touk-jira.atlassian.net/browse/NU-2194
             * When node is added from a toolbar, branchParametersTemplate are initially added to the node, but when we perform a scenario save, node has no branchParametersTemplate
             * Let's ignore branchParametersTemplate in a button save state checking
             */
            node.branchParametersTemplate = undefined;

            if (mode === "execution") {
                node.additionalFields = undefined;
                const parametersDefinitions = nodesDetails?.[node.id]?.parameters;
                const parameters = findParameters(node);
                parametersDefinitions?.forEach((def, index) => {
                    if (def.nonImportantForExecution) {
                        parameters[index] = undefined;
                    }
                });
            }
        });
    });
}

export function isGraphUpdated(scenarioGraph: ScenarioGraph, savedScenarioGraph: ScenarioGraph, mode?: "save");
export function isGraphUpdated(
    scenarioGraph: ScenarioGraph,
    savedScenarioGraph: ScenarioGraph,
    mode: "execution",
    nodesDetails: NodeDetailsState,
);
export function isGraphUpdated(
    scenarioGraph: ScenarioGraph,
    savedScenarioGraph: ScenarioGraph,
    mode: "save" | "execution" = "save",
    nodesDetails?: NodeDetailsState,
) {
    const filter = filterScenarioData(mode, nodesDetails);
    return !isEqual(filter(scenarioGraph), filter(savedScenarioGraph));
}

function sorted(labels: string[]) {
    return labels.slice().sort((a, b) => a.localeCompare(b));
}

export function areLabelsUpdated(labels: string[] = [], savedLabels: string[] = []) {
    return !isEqual(sorted(labels), sorted(savedLabels));
}
