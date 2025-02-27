import { isAggregate } from "../../components/graph/node-modal/customNode";
import { Component, NodeType } from "../../types";

const prefix = `㊙️㊙️`;
const suffix = `㊙️㊙️`;

const getFakeVarName = (type: string) => `${prefix}${type}${suffix}`;

export const fakeNodeCreatorType = (node: NodeType): string | null => {
    if (node.type === "VariableBuilder") {
        if (node.additionalFields?.creatorType) {
            return node.additionalFields?.creatorType;
        }
        const regExp = new RegExp(`${prefix}(.*)${suffix}`);
        return regExp.exec(node.varName)?.[1];
    }
    return null;
};

export function getCreatorTypeFromFakeVar(varName: string) {
    const regExp = new RegExp(`${prefix}(.*)${suffix}`);
    return regExp.exec(varName)?.[1];
}

export const getCreatorType = (node: NodeType): string | null => {
    if (isAggregate(node)) {
        return "aggregate";
    }
    if (node.additionalFields?.creatorType) {
        return node.additionalFields?.creatorType;
    }
    if (node.type === "VariableBuilder") {
        return getCreatorTypeFromFakeVar(node.varName);
    }
    return null;
};

export const fakeComponentType = `componentsCreator`;

export const getCreator = (type: string): Component => ({
    componentId: `${fakeComponentType}-${type}`,
    label: type,
    node: {
        id: type,
        type: "VariableBuilder",
        varName: getFakeVarName(type),
        additionalFields: {
            creatorType: type,
        },
        fields: [],
    },
});
