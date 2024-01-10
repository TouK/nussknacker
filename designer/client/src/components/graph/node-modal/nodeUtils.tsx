import { NodeType } from "../../../types";
import { cloneDeep, get, has } from "lodash";
import { v4 as uuid4 } from "uuid";
import { Scenario } from "src/components/Process/types";

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
    return scenario.json.properties.isFragment ? node.id.replace(`${scenario.name}-`, "") : node.id;
}
