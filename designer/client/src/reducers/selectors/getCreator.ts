import { isAggregate } from "../../common/componentUtils";
import type { NodeType } from "../../types/node";

const prefix = `㊙️㊙️`;
const suffix = `㊙️㊙️`;
const fakeVarRegExp = new RegExp(`${prefix}(.*)${suffix}`);

export function getFakeVarName(type: string) {
    return `${prefix}${type}${suffix}`;
}

function getCreatorTypeFromFakeVar(varName: string) {
    return fakeVarRegExp.exec(varName)?.[1];
}

export const fakeNodeCreatorType = (node: NodeType): string | null => {
    if (node.type === "VariableBuilder") {
        if (node.additionalFields?.creatorType) {
            return node.additionalFields?.creatorType;
        }
        return getCreatorTypeFromFakeVar(node.varName);
    }
    return null;
};

export const getCreatorType = (node: NodeType): string | null => {
    if (isAggregate(node)) {
        return "aggregate";
    }
    if (node.additionalFields?.creatorType) {
        return node.additionalFields.creatorType;
    }
    if (node.type === "VariableBuilder") {
        return getCreatorTypeFromFakeVar(node.varName);
    }
    return null;
};
