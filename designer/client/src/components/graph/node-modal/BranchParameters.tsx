import { FormLabel, styled } from "@mui/material";
import React from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import type { NodeResultsForContext } from "../../../common/TestResultUtils";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import ExpressionField from "./editors/expression/ExpressionField";
import { FormControl } from "./editors/FormControl";
import { getValidationErrorsForField } from "./editors/Validators";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";

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

                                    if (!paramValue) {
                                        return null;
                                    }

                                    return (
                                        <ExpressionField
                                            key={`${paramName}-${branchId}`}
                                            fieldName={fieldName}
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
                                            fieldErrors={getValidationErrorsForField(errors, fieldName)}
                                        />
                                    );
                                })}
                            </StyledFieldControl>
                        </div>
                    </FormControl>
                );
            })}
        </>
    );
}
