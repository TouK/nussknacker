import { isEqual } from "lodash";

import type { NodeType, ScenarioGraph } from "../../types";
import type { NestedKeyOf } from "../graph/lodashWrappers";
import { omit } from "../graph/lodashWrappers";

function omitNodeFields({ nodes = [], ...details }: ScenarioGraph, paths: NestedKeyOf<NodeType>[]) {
    return {
        ...details,
        nodes: nodes.map((node) => omit(node, paths)),
    };
}

export function isGraphUpdated(scenarioGraph: ScenarioGraph, savedScenarioGraph: ScenarioGraph, ignoreUiParams = false) {
    /**
     * It's a fix of https://touk-jira.atlassian.net/browse/NU-2194
     * When node is added from a toolbar, branchParametersTemplate are initially added to the node, but when we perform a scenario save, node has no branchParametersTemplate
     * Let's ignore branchParametersTemplate in a button save state checking
     */
    const paths: NestedKeyOf<NodeType>[] = ["branchParametersTemplate"];

    if (ignoreUiParams) {
        paths.push("additionalFields");
    }

    return !isEqual(omitNodeFields(scenarioGraph, paths), omitNodeFields(savedScenarioGraph, paths));
}

function sorted(labels: string[]) {
    return labels.slice().sort((a, b) => a.localeCompare(b));
}

export function areLabelsUpdated(labels: string[] = [], savedLabels: string[] = []) {
    return !isEqual(sorted(labels), sorted(savedLabels));
}
