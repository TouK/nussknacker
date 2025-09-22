import { defaultsDeep, sortBy } from "lodash";

import type { Layout } from "../actions/nk/ui/layout";
import type { Reducer } from "../actions/reduxTypes";
import type { NodeType } from "../types/node";
import type { ScenarioGraph } from "../types/scenarioGraph";

export function fromMeta(scenarioGraph: ScenarioGraph): Layout {
    return sortBy(scenarioGraph.nodes, (e) => e.id)
        .filter(({ additionalFields }) => additionalFields?.layoutData)
        .map(({ id, additionalFields }) => ({ id, position: additionalFields.layoutData }));
}

export const nodes: Reducer<NodeType[]> = (nodes, action) => {
    switch (action.type) {
        case "LAYOUT_RELOADED":
        case "LAYOUT_CHANGED":
            return nodes?.map((node) => {
                const layoutData = action.layout.find(({ id }) => id === node.id)?.position || null;
                return defaultsDeep({ additionalFields: { layoutData } }, node);
            });
        default:
            return nodes;
    }
};
