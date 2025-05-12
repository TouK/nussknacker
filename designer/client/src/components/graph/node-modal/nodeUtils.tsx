import { cloneDeep, get, has } from "lodash";
import type { Scenario } from "src/components/Process/types";
import { v4 as uuid4 } from "uuid";

import type { NodeType } from "../../../types";

export function generateUUIDs(editedNode: NodeType, properties: string[]): NodeType {
    const node = cloneDeep(editedNode);
    properties.forEach((property) => {
        if (has(node, property)) {
            get(node, property, []).forEach((el) => (el.uuid = el.uuid || uuid4()));
        }
    });
    return node;
}

export function getNodeId(scenario: Scenario, node: NodeType): string {
    return scenario.isFragment ? node.id.replace(`${scenario.name}-`, "") : node.id;
}
