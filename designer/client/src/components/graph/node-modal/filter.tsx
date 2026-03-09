import React, { useMemo } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import type { UIParameter } from "../../../types/definition";
import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import { FilterConditionBuilder } from "../../conditionBuilder/FilterConditionBuilder";
import { DescriptionField } from "./DescriptionField";
import { DisableField } from "./DisableField";
import { EdgesDndComponent } from "./EdgesDndComponent";
import { IdField } from "./IdField";
import { useDiffMark } from "./PathsToMark";
import { StaticExpressionField } from "./StaticExpressionField";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export function Filter({
    edges,
    errors,
    variableTypes,
    isEditMode,
    node,
    parameterDefinitions,
    setEditedEdges,
    setProperty,
    showSwitch,
    showValidation,
}: {
    edges: Edge[];
    errors: NodeValidationError[];
    variableTypes?: VariableTypes;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setEditedEdges: (edges: Edge[]) => void;
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}): React.JSX.Element {
    const [, isCompareView] = useDiffMark();
    const [showConditionBuilder] = useUserSettings("node.showConditionBuilder");
    const edgeTypes = useMemo(
        () => [
            { value: EdgeKind.filterTrue, onlyOne: true },
            { value: EdgeKind.filterFalse, onlyOne: true },
        ],
        [],
    );
    return (
        <>
            <IdField isEditMode={isEditMode} showValidation={showValidation} node={node} setProperty={setProperty} errors={errors} />
            <StaticExpressionField
                setProperty={setProperty}
                fieldLabel={"Condition"}
                parameterDefinitions={parameterDefinitions}
                showSwitch={showSwitch}
                variableTypes={variableTypes}
                showValidation={showValidation}
                errors={errors}
                isEditMode={isEditMode}
                node={node}
                endAdornment={
                    isEditMode && showConditionBuilder ? (
                        <FilterConditionBuilder
                            node={node}
                            variableTypes={variableTypes}
                            setProperty={setProperty}
                            expression={node.expression}
                        />
                    ) : null
                }
            />
            <DisableField node={node} isEditMode={isEditMode} showValidation={showValidation} setProperty={setProperty} errors={errors} />
            {!isCompareView ? (
                <EdgesDndComponent
                    label={"Outputs"}
                    nodeId={node.id}
                    value={edges}
                    onChange={setEditedEdges}
                    edgeTypes={edgeTypes}
                    readOnly={!isEditMode}
                    errors={errors}
                />
            ) : null}
            <DescriptionField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
