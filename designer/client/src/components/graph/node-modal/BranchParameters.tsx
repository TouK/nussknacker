import React from "react";
import ExpressionField from "./editors/expression/ExpressionField";
import ProcessUtils from "../../../common/ProcessUtils";
import { NodeType, NodeValidationError, UIParameter } from "../../../types";
import { NodeResultsForContext } from "../../../common/TestResultUtils";
import { getValidationErrorsForField } from "./editors/Validators";
import { FormControl, FormLabel } from "@mui/material";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";

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
                        <div className={nodeValue}>
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
                                        <ExpressionField
                                            key={`${paramName}-${branchId}`}
                                            fieldName={`${paramName} for branch ${branchId}`}
                                            fieldLabel={branchId}
                                            exprPath={expressionPath}
                                            isEditMode={isEditMode}
                                            editedNode={node}
                                            showValidation={showValidation}
                                            showSwitch={showSwitch}
                                            parameterDefinition={param}
                                            setNodeDataAt={setNodeDataAt}
                                            testResultsToShow={testResultsToShow}
                                            renderFieldLabel={(paramName) => <FormLabel>{paramName}</FormLabel>}
                                            variableTypes={variables}
                                            fieldErrors={getValidationErrorsForField(errors, paramName)}
                                        />
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
