import { groupBy } from "lodash";
import { createSelector } from "reselect";
import { stickyNoteComponentGroup } from "../../components/toolbars/creator/StickyNoteComponent";
import { RootState } from "../index";
import { getCreator } from "./getCreator";
import { isPristine } from "./graph";
import { getAdditionalComponents } from "./isCloudInstance";
import { getProcessDefinitionData, getStickyNotesSettings } from "./settings";

export const getComponentGroups = createSelector(
    getProcessDefinitionData,
    getStickyNotesSettings,
    isPristine,
    getAdditionalComponents,
    ({ componentGroups }, stickyNotesSettings, pristine, additionalCreators) => {
        let groups = componentGroups;

        if (stickyNotesSettings.enabled) {
            groups = groups.concat(stickyNoteComponentGroup(pristine));
        }

        if (additionalCreators.length) {
            groups = groups.concat({
                name: "debug ㊙️",
                components: additionalCreators.map(getCreator),
            });
        }

        return groups;
    },
);

export const getConfiguredAdditionalComponents = createSelector(
    getProcessDefinitionData,
    (state: RootState) => state.cloudData.configuredComponents,
    ({ componentGroups }, configured) => {
        return groupBy(
            componentGroups
                .flatMap((g) => g.components)
                .flatMap(({ componentId }) =>
                    configured.map(({ name, type }) => (componentId.includes(name) ? { componentId, type } : null)).filter(Boolean),
                ),
            ({ type }) => type,
        );
    },
);
