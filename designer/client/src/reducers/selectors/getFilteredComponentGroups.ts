import { isEqual } from "lodash";
import { useMemo } from "react";
import { createSelector } from "reselect";

import { filterComponentsByLabel } from "../../common/ProcessDefinitionUtils";
import NodeUtils from "../../components/graph/NodeUtils";
import { ComponentFilter } from "../../components/toolbars/creator/ToolBox";
import { useAppSelector } from "../../store/storeHelpers";
import type { ComponentGroup } from "../../types/component";
import { getProcessDefinitionData } from "./getProcessDefinitionData";

const EMPTY_ARRAY = [];

const getOptionalProps = (_, props?: { filters?: ComponentFilter[]; textFilters?: string[] }) => ({
    filters: props?.filters || EMPTY_ARRAY,
    textFilters: props?.textFilters || EMPTY_ARRAY,
});

export const getFilteredComponentGroups = createSelector(
    getProcessDefinitionData,
    getOptionalProps,
    (definitionData, { filters, textFilters }): ComponentGroup[] => {
        return definitionData.componentGroups
            .map((group) => ({
                ...group,
                components: group.components.filter((component) =>
                    filters.every((f) => {
                        switch (f) {
                            case ComponentFilter.removeNoInputs:
                                return NodeUtils.hasInputs(component.node);
                            case ComponentFilter.removeNoOutputs:
                                return NodeUtils.hasOutputs(component.node, definitionData);
                            case ComponentFilter.sourcesOnly:
                                return (
                                    ["Source", "FragmentInputDefinition"].includes(component.node.type) ||
                                    component.node.additionalFields?.creatorType
                                );
                        }
                    }),
                ),
            }))
            .map((group) => filterComponentsByLabel(textFilters)(group))
            .filter((g) => g.components.length > 0);
    },
);

export const useFilteredComponentGroups = (
    filters: ComponentFilter[] = EMPTY_ARRAY,
    textFilters: string[] = EMPTY_ARRAY,
): ComponentGroup[] => {
    return useAppSelector((state) => getFilteredComponentGroups(state, { filters, textFilters }), isEqual);
};
