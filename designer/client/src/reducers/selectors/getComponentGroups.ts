import { createSelector } from "reselect";
import { StickyNotesSettings } from "../../actions/nk";
import { stickyNoteComponentGroup } from "../../components/toolbars/creator/StickyNoteComponent";
import { ComponentGroup } from "../../types";
import { RootState } from "../index";
import { getCreator } from "./getCreator";
import { isFragment, isPristine } from "./graph";
import { getProcessDefinitionData, getStickyNotesSettings } from "./settings";

function addUniqueElement<T extends { name: string }>(array: T[], newElement: T): T[] {
    let found;
    const updatedArray = array.map((item) => {
        return item.name === newElement.name ? (found = newElement) : item;
    });
    if (!found) updatedArray.push(newElement);
    return updatedArray;
}

export const appendAdditionalCreators = (groups: ComponentGroup[], additionalCreators: string[]) => {
    if (!additionalCreators.length) return groups;
    const newElement = {
        name: "cloud addons",
        components: additionalCreators.map(getCreator),
    };
    return addUniqueElement(groups, newElement);
};

function appendStickyNotes(groups: ComponentGroup[], stickyNotesSettings: StickyNotesSettings, pristine: boolean) {
    if (!stickyNotesSettings.enabled) return groups;
    return groups.concat(stickyNoteComponentGroup(pristine));
}

function replaceOrAdd<T>(collection: T[] = [], predicate: (item: T) => boolean, replaceOrAddFn: (item?: T) => T): T[] {
    const index = collection.findIndex(predicate);
    if (index === -1) return [...collection, replaceOrAddFn()];

    const currentItem = collection[index];
    return [...collection.slice(0, index), replaceOrAddFn(currentItem), ...collection.slice(index + 1)];
}

function appendFragmentCreator(groups: ComponentGroup[], isFragment?: boolean) {
    if (isFragment) return groups;
    const groupName = "fragments";

    const fragmentCreator = {
        label: "new fragment",
        componentId: "fragment-.template",
        node: {
            id: "",
            ref: {
                id: ".template",
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
        ({ name }) => name === groupName,
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
}

export const getComponentGroups = createSelector(
    getProcessDefinitionData,
    getStickyNotesSettings,
    isPristine,
    isFragment,
    ({ componentGroups }, stickyNotesSettings, pristine, isFragment) => {
        const withStickyNotes = appendStickyNotes(componentGroups, stickyNotesSettings, pristine);
        const groups = appendFragmentCreator(withStickyNotes, isFragment);
        return groups;
    },
);

const cloudConfiguredComponents = (state: RootState) => state.cloudData?.configuredComponents;

export const getConfiguredAdditionalComponents = createSelector(
    getProcessDefinitionData,
    cloudConfiguredComponents,
    ({ componentGroups }, configured) => {
        return componentGroups
            .flatMap((g) => g.components)
            .flatMap(({ componentId }) =>
                configured.map(({ name, type }) => (componentId.includes(name) ? { componentId, type } : null)).filter(Boolean),
            );
    },
);
