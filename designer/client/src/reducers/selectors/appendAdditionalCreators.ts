import { curryRight } from "lodash";

import type { Component, ComponentGroup } from "../../types/component";
import { getFakeVarName } from "./getCreator";

function addUniqueElement<T extends { name: string }>(array: T[], newElement: T): T[] {
    let found;
    const updatedArray = array.map((item) => {
        return item.name === newElement.name ? (found = newElement) : item;
    });
    if (!found) updatedArray.push(newElement);
    return updatedArray;
}

export const fakeComponentType = `componentsCreator`;

const getCreator = (type: string): Component => ({
    componentId: `${fakeComponentType}-${type}`,
    label: type,
    node: {
        id: type,
        name: type,
        type: "VariableBuilder",
        varName: getFakeVarName(type),
        additionalFields: {
            creatorType: type,
        },
        fields: [],
    },
});

export const appendAdditionalCreators = curryRight((groups: ComponentGroup[], additionalCreators: string[]) => {
    if (!additionalCreators.length) return groups;
    const newElement = {
        name: "Cloud addons",
        components: additionalCreators.map(getCreator),
    };
    return addUniqueElement(groups, newElement);
});
