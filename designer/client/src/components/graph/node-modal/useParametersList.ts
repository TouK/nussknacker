import { useEffect, useMemo, useState } from "react";
import { NodeType, Parameter, ProcessDefinitionData } from "../../../types";

export const useParametersList = (
    editedNode: NodeType,
    processDefinitionData: ProcessDefinitionData,
    isEditMode: boolean,
    setNodeState: (newParams: Parameter[]) => void,
) => {
    const { type, ref } = editedNode;
    const { componentGroups } = processDefinitionData;
    const [initialized, setInitialized] = useState(false);

    const nodeDefinition = useMemo(
        () => componentGroups?.flatMap((g) => g.components)?.find((n) => n.node.type === type && n.label === ref.id)?.node,
        [componentGroups, ref.id, type],
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
