import { isEqual, omit } from "lodash";

import type { ScenarioGraph } from "../../types";

/**
 * It's a fix of https://touk-jira.atlassian.net/browse/NU-2194
 * When node is added from a toolbar, branchParametersTemplate are initially added to the node, but when we perform a scenario save, node has no branchParametersTemplate
 * Let's ignore branchParametersTemplate in a button save state checking
 */
function omitBranchParametersTemplate({ nodes = [], ...details }: ScenarioGraph) {
    return {
        ...details,
        nodes: nodes.map((node) => omit(node, ["branchParametersTemplate"])),
    };
}

export function isGraphUpdated(scenarioGraph: ScenarioGraph, savedScenarioGraph: ScenarioGraph) {
    return isEqual(omitBranchParametersTemplate(scenarioGraph), omitBranchParametersTemplate(savedScenarioGraph));
}

function sorted(labels: string[]) {
    return labels.slice().sort((a, b) => a.localeCompare(b));
}

export function areLabelsUpdated(labels: string[] = [], savedLabels: string[] = []) {
    return isEqual(sorted(labels), sorted(savedLabels));
}
