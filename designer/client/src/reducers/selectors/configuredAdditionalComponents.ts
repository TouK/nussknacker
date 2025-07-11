import { createSelector } from "reselect";

import type { RootState } from "../index";
import { getProcessDefinitionData } from "./getProcessDefinitionData";

const cloudConfiguredComponents = (state: RootState) => state.cloudData?.configuredComponents;

export const getConfiguredAdditionalComponents = createSelector(
    getProcessDefinitionData,
    cloudConfiguredComponents,
    ({ componentGroups }, configured = []) =>
        componentGroups
            .flatMap((g) => g.components)
            .flatMap(({ componentId }) =>
                configured
                    .map(({ name, type }) =>
                        componentId.includes(`-${name}-`) && !componentId.startsWith("fragment")
                            ? {
                                  componentId,
                                  type,
                              }
                            : null,
                    )
                    .filter(Boolean),
            ),
);
