import React, { ComponentType, useEffect, useMemo, useState } from "react";
import { NodeType, Parameter, ProcessDefinitionData } from "../../../types";

interface ParameterListProps {
    processDefinitionData: ProcessDefinitionData;
    editedNode: NodeType;
    setNodeState: (newParams: Parameter[]) => void;
    ListField: ComponentType<{ param: Parameter; path: string }>;
    isEditMode?: boolean;
}

export default function ParameterList({ ListField, editedNode, processDefinitionData, setNodeState, isEditMode }: ParameterListProps) {
    const { type, ref } = editedNode;
    const { componentGroups } = processDefinitionData;

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

    useEffect(() => {
        if (leaveRedundant) {
            return;
        }
        setNodeState(parameters);
    }, [leaveRedundant, parameters, setNodeState]);

    return (
        <>
            {parameters.map((param, index) => (
                <div className="node-block" key={param.name + index}>
                    <ListField param={param} path={`ref.parameters[${index}]`} />
                </div>
            ))}
        </>
    );
}
