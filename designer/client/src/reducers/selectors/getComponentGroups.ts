import { createSelector } from "reselect";
import { StickyNotesSettings } from "../../actions/nk";
import { stickyNoteComponentGroup } from "../../components/toolbars/creator/StickyNoteComponent";
import { ComponentGroup } from "../../types";
import { RootState } from "../index";
import { getCreator } from "./getCreator";
import { isPristine } from "./graph";
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
        name: "debug ㊙️",
        components: additionalCreators.map(getCreator),
    };
    return addUniqueElement(groups, newElement);
};

function appendStickyNotes(groups: ComponentGroup[], stickyNotesSettings: StickyNotesSettings, pristine: boolean) {
    if (!stickyNotesSettings.enabled) return groups;
    return groups.concat(stickyNoteComponentGroup(pristine));
}

export const getComponentGroups = createSelector(
    getProcessDefinitionData,
    getStickyNotesSettings,
    isPristine,
    ({ componentGroups }, stickyNotesSettings, pristine) => {
        return appendStickyNotes(componentGroups, stickyNotesSettings, pristine);
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
