import { styled } from "@mui/material";
import React from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import type { NodeResultsForContext } from "../../../common/TestResultUtils";
import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import ExpressionField from "./editors/expression/ExpressionField";
import { FieldLabelProvider } from "./editors/RenderFieldLabel";
import { getValidationErrorsForField } from "./editors/Validators";
import { NodeRow } from "./node/NodeRow";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";

const StyledFieldControl = styled("div")(() => ({
    ".MuiFormControl-root": {
        margin: 0,
    },
}));
export interface BranchParametersProps {
    node: NodeType;
    parameterDefinitions: UIParameter[];
    errors: NodeValidationError[];
    setNodeDataAt: <T>(propToMutate: string, newValue: T, defaultValue?: T) => void;
    testResultsToShow: NodeResultsForContext;
    isEditMode?: boolean;
    showValidation?: boolean;
    showSwitch?: boolean;
}

export default function BranchParameters({
    node,
    showValidation,
    errors,
    showSwitch,
    isEditMode,
    parameterDefinitions,
    setNodeDataAt,
    testResultsToShow,
}: BranchParametersProps): React.JSX.Element {
    //TODO: maybe we can rely only on node?
    const branchParameters = parameterDefinitions?.filter((p) => p.branchParam);
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const scenarioGraph = useAppSelector(getScenarioGraph);
    const nodeNameById = React.useMemo<Record<string, string>>(() => {
        return Object.fromEntries((scenarioGraph.nodes || []).map((graphNode) => [graphNode.id, graphNode.name]));
    }, [scenarioGraph.nodes]);

    return (
        <FieldLabelProvider>
            {branchParameters?.map((param) => {
                const paramName = param.name;
                return (
                    <NodeRow key={paramName} label={paramName}>
                        <div className={nodeValue}>
                            <StyledFieldControl className="fieldsControl">
                                {node.branchParameters.map((branchParameter, branchIndex) => {
                                    const branchId = branchParameter.branchId;
                                    //here we assume the parameters are correct wrt branch definition. If this is not the case,
                                    //differences should be handled on other level, e.g. using reducers etc.
                                    const paramIndex = branchParameter.parameters.findIndex(
                                        (paramInBranch) => paramInBranch.name === paramName,
                                    );
                                    const paramValue = branchParameter.parameters[paramIndex];
                                    const expressionPath = `branchParameters[${branchIndex}].parameters[${paramIndex}].expression`;

                                    const contextId = ProcessUtils.findContextForBranch(node, branchId);
                                    const variables = findAvailableVariables(contextId, param);
                                    const fieldName = `${paramName} for branch ${branchId}`;
                                    const fieldLabel = nodeNameById[branchId] || branchId;
                                    const fieldErrors = getValidationErrorsForField(errors, fieldName).map((error) => {
                                        if (fieldLabel === branchId) {
                                            return error;
                                        }
                                        const replaceBranchId = (text: string): string => text.split(branchId).join(fieldLabel);
                                        return {
                                            ...error,
                                            message: replaceBranchId(error.message),
                                            description: replaceBranchId(error.description),
                                        };
                                    });

                                    if (!paramValue) {
                                        return null;
                                    }

                                    return (
                                        <ExpressionField
                                            key={`${paramName}-${branchId}`}
                                            fieldName={fieldName}
                                            fieldLabel={fieldLabel}
                                            exprPath={expressionPath}
                                            isEditMode={isEditMode}
                                            editedNode={node}
                                            showValidation={showValidation}
                                            showSwitch={showSwitch}
                                            parameterDefinition={param}
                                            setNodeDataAt={setNodeDataAt}
                                            testResultsToShow={testResultsToShow}
                                            variableTypes={variables}
                                            fieldErrors={fieldErrors}
                                        />
                                    );
                                })}
                            </StyledFieldControl>
                        </div>
                    </NodeRow>
                );
            })}
        </FieldLabelProvider>
    );
}
