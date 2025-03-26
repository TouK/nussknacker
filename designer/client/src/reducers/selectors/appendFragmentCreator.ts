import { curryRight } from "lodash";
import type { ComponentGroup } from "../../types";

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
