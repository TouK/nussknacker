import { determineComponentId } from "../../../common/componentUtils";
import type { NodeType } from "../../../types/node";

export function isAggregate(node: NodeType) {
    return ["custom-aggregate-session", "custom-aggregate-sliding", "custom-aggregate-tumbling"].includes(determineComponentId(node));
}
