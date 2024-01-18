import React from "react";
import ExpressionField from "./editors/expression/ExpressionField";
import ProcessUtils from "../../../common/ProcessUtils";
import { NodeType, NodeValidationError, UIParameter } from "../../../types";
import { NodeResultsForContext } from "../../../common/TestResultUtils";
import { BranchParameterRowStyled } from "../focusableStyled";
import { getValidationErrorsForField } from "./editors/Validators";
import { FormControl, FormLabel } from "@mui/material";

export interface BranchParametersProps {
    node: NodeType;
    parameterDefinitions: UIParameter[];
    errors: NodeValidationError[];
    setNodeDataAt: <T>(propToMutate: string, newValue: T, defaultValue?: T) => void;
    findAvailableVariables: ReturnType<typeof ProcessUtils.findAvailableVariables>;
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
    findAvailableVariables,
}: BranchParametersProps): JSX.Element {
    //TODO: maybe we can rely only on node?
    const branchParameters = parameterDefinitions?.filter((p) => p.branchParam);

    return (
        <>
            {branchParameters?.map((param) => {
                const paramName = param.name;
                return (
                    <FormControl key={paramName}>
                        <FormLabel title={paramName}>{paramName}:</FormLabel>
                        <div className="node-value">
                            <div className="fieldsControl">
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

                                    if (!paramValue) {
                                        return null;
                                    }

                                    return (
                                        <BranchParameterRowStyled key={`${paramName}-${branchId}`}>
                                            <div className={"branch-param-label"}>{branchId}</div>
                                            <div className={"branch-parameter-expr-container"}>
                                                <ExpressionField
                                                    fieldName={`${paramName} for branch ${branchId}`}
                                                    fieldLabel={paramName}
                                                    exprPath={expressionPath}
                                                    isEditMode={isEditMode}
                                                    editedNode={node}
                                                    showValidation={showValidation}
                                                    showSwitch={showSwitch}
                                                    parameterDefinition={param}
                                                    setNodeDataAt={setNodeDataAt}
                                                    testResultsToShow={testResultsToShow}
                                                    renderFieldLabel={() => false}
                                                    variableTypes={variables}
                                                    fieldErrors={getValidationErrorsForField(errors, paramName)}
                                                />
                                            </div>
                                        </BranchParameterRowStyled>
                                    );
                                })}
                            </div>
                        </div>
                    </FormControl>
                );
            })}
        </>
    );
}
