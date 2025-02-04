import { isAggregate } from "../../components/graph/node-modal/customNode";
import { Component, NodeType } from "../../types";

const prefix = `㊙️㊙️`;
const suffix = `㊙️㊙️`;

const getFakeVarName = (type: string) => `${prefix}${type}${suffix}`;

export const getCreatorType = (node: NodeType): string | null => {
    if (node.additionalFields?.virtualNode) {
        return node.additionalFields?.virtualNode;
    }
    if (node.type === "VariableBuilder") {
        const regExp = new RegExp(`${prefix}(.*)${suffix}`);
        return regExp.exec(node.varName)?.[1];
    }
    if (isAggregate(node)) {
        return "aggregate";
    }
    return null;
};

export const getCreator = (type: string): Component => ({
    componentId: `testCreator_${type}`,
    label: type,
    node: {
        id: type,
        type: "VariableBuilder",
        varName: getFakeVarName(type),
        additionalFields: {
            description: `fake node to create **${type}** datasource`,
            virtualNode: type,
        },
        fields: [],
    },
});
