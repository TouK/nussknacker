import { get, has, omit } from "lodash";
import type { Scenario } from "src/components/Process/types";
import { v4 as uuid4 } from "uuid";

import type { NodeType } from "../../../types";
import { setImmutable } from "./setImmutable";

export function generateUUIDs(editedNode: Readonly<NodeType>, properties: Readonly<string[]>): NodeType {
    return properties
        .filter((property) => has(editedNode, property))
        .reduce(
            (modifiedNode, property) =>
                setImmutable(
                    modifiedNode,
                    property,
                    get(editedNode, property, []).map((e) =>
                        e.uuid
                            ? e
                            : {
                                  ...e,
                                  uuid: uuid4(),
                              },
                    ),
                ),
            editedNode,
        );
}

export function removeUUIDs(node: Readonly<NodeType>, properties: Readonly<string[]>): NodeType {
    return properties
        .filter((property) => has(node, property))
        .reduce(
            (modifiedNode, property) =>
                setImmutable(
                    modifiedNode,
                    property,
                    get(modifiedNode, property, []).map((item) => (item.uuid ? omit(item, ["uuid"]) : item)),
                ),
            node,
        );
}

export function getNodeId(scenario: Scenario, node: NodeType): string {
    return scenario.isFragment ? node.id.replace(`${scenario.name}-`, "") : node.id;
}
