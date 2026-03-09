import { useEffect, useMemo, useState } from "react";

import { determineComponentId } from "../../../common/componentUtils";
import type { NodeType, Parameter } from "../../../types/node";
import type { ProcessDefinitionData } from "../../../types/scenarioGraph";

export const useParametersList = (
    editedNode: NodeType,
    processDefinitionData: ProcessDefinitionData,
    isEditMode: boolean,
    setNodeState: (newParams: Parameter[]) => void,
) => {
    const { ref } = editedNode;
    const { componentGroups } = processDefinitionData;
    const [initialized, setInitialized] = useState(false);
    const componentId = determineComponentId(editedNode);

    const nodeDefinition = useMemo(
        () => componentGroups?.flatMap((g) => g.components)?.find((component) => component.componentId === componentId)?.node,
        [componentGroups, componentId],
    );

    const leaveRedundant = !nodeDefinition || !isEditMode;

    const [parameters] = useState(() => {
        const savedParameters = ref.parameters;

        if (leaveRedundant) {
            return savedParameters;
        }

        return nodeDefinition?.ref.parameters.map(
            (definition) => savedParameters.find(({ name }) => name === definition.name) || definition,
        );
    });

    // Don't return parameters until node state updated
    useEffect(() => {
        if (!leaveRedundant) {
            setNodeState(parameters);
        }

        setInitialized(true);
    }, [leaveRedundant, parameters, setNodeState]);

    if (!initialized) {
        return [];
    }
    return parameters;
};
