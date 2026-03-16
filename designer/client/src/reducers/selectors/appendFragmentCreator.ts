import { curryRight } from "lodash";

import NodeUtils from "../../components/graph/NodeUtils";
import type { ComponentGroup } from "../../types/component";
import type { NodeType } from "../../types/node";
import type { ProcessDefinitionData } from "../../types/scenarioGraph";

function replaceOrAdd<T>(collection: T[] = [], predicate: (item: T) => boolean, replaceOrAddFn: (item?: T) => T): T[] {
    const index = collection.findIndex(predicate);
    if (index === -1) return [...collection, replaceOrAddFn()];

    const currentItem = collection[index];
    return [...collection.slice(0, index), replaceOrAddFn(currentItem), ...collection.slice(index + 1)];
}

export const FRAGMENT_TEMPLATE_ID = `.template`;
export const appendFragmentCreator = curryRight((groups: ComponentGroup[], isFragment: boolean) => {
    if (isFragment) return groups;
    const groupName = "Fragments";

    const fragmentCreator = {
        label: "new fragment",
        componentId: `fragment-${FRAGMENT_TEMPLATE_ID}`,
        node: {
            id: "",
            name: "new fragment",
            ref: {
                id: FRAGMENT_TEMPLATE_ID,
                parameters: [],
                outputVariableNames: {
                    output: "output",
                },
            },
            additionalFields: null,
            isDisabled: null,
            fragmentParams: null,
            type: "FragmentInput",
            branchParametersTemplate: [],
        },
        branchParametersTemplate: [],
    };

    return replaceOrAdd<ComponentGroup>(
        groups,
        ({ name }) => name.toLowerCase() === groupName.toLowerCase(),
        (
            { components, ...group } = {
                name: groupName,
                components: [],
            },
        ) => ({
            ...group,
            components: [...components, fragmentCreator as any],
        }),
    );
});

export function isFragmentCreator(node: NodeType, processDefinitionData: ProcessDefinitionData) {
    if (NodeUtils.isAvailable(node, processDefinitionData)) return false;
    return node.ref.id === FRAGMENT_TEMPLATE_ID;
}
