import { compact, curryRight, flow, Many } from "lodash";
import { createSelector } from "reselect";
import { StickyNotesSettings } from "../../actions/nk";
import { stickyNoteComponentGroup } from "../../components/toolbars/creator/StickyNoteComponent";
import { ComponentGroup } from "../../types";
import { getCreator } from "./getCreator";
import { isFragment, isPristine } from "./graph";
import { getAdditionalComponents } from "./isCloudInstance";
import { getStickyNotesSettings } from "./settings";
import { getUserSettings } from "./userSettings";

function addUniqueElement<T extends { name: string }>(array: T[], newElement: T): T[] {
    let found;
    const updatedArray = array.map((item) => {
        return item.name === newElement.name ? (found = newElement) : item;
    });
    if (!found) updatedArray.push(newElement);
    return updatedArray;
}

const appendAdditionalCreators = curryRight((groups: ComponentGroup[], additionalCreators: string[]) => {
    if (!additionalCreators.length) return groups;
    const newElement = {
        name: "Cloud addons",
        components: additionalCreators.map(getCreator),
    };
    return addUniqueElement(groups, newElement);
});

const appendStickyNotes = curryRight((groups: ComponentGroup[], stickyNotesSettings: StickyNotesSettings, pristine: boolean) => {
    if (!stickyNotesSettings.enabled) return groups;
    return groups.concat(stickyNoteComponentGroup(pristine));
});

function replaceOrAdd<T>(collection: T[] = [], predicate: (item: T) => boolean, replaceOrAddFn: (item?: T) => T): T[] {
    const index = collection.findIndex(predicate);
    if (index === -1) return [...collection, replaceOrAddFn()];

    const currentItem = collection[index];
    return [...collection.slice(0, index), replaceOrAddFn(currentItem), ...collection.slice(index + 1)];
}

export const FRAGMENT_TEMPLATE_ID = `.template`;
const appendFragmentCreator = curryRight((groups: ComponentGroup[], isFragment: boolean) => {
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

const compactFlow = (...func: Array<Many<(...args: any[]) => any>>) => flow(...compact(func));

export const getComponentGroupsExtender = createSelector(
    getStickyNotesSettings,
    isPristine,
    isFragment,
    getUserSettings,
    getAdditionalComponents,
    (stickyNotesSettings, pristine, isFragment, userSettings, additionalComponents): ((c: ComponentGroup[]) => ComponentGroup[]) =>
        compactFlow(
            userSettings["node.fragmentCreator"] && appendFragmentCreator(isFragment),
            userSettings["cloud.componentCreators"] && appendAdditionalCreators(additionalComponents),
            appendStickyNotes(stickyNotesSettings, pristine),
        ),
);
