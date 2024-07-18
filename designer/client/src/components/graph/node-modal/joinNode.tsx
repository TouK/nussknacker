import { useTestResults } from "./TestResultsWrapper";
import React from "react";
import BranchParameters from "./BranchParameters";
import { CustomNode, CustomNodeProps } from "./customNode";

export function JoinNode(props: CustomNodeProps): JSX.Element {
    const { errors, findAvailableVariables, isEditMode, node, parameterDefinitions, setProperty, showSwitch, showValidation } = props;
    const testResultsState = useTestResults();
    return (
        <CustomNode {...props}>
            <BranchParameters
                node={node}
                showValidation={showValidation}
                showSwitch={showSwitch}
                isEditMode={isEditMode}
                errors={errors || []}
                parameterDefinitions={parameterDefinitions}
                setNodeDataAt={setProperty}
                testResultsToShow={testResultsState.testResultsToShow}
                findAvailableVariables={findAvailableVariables}
            />
        </CustomNode>
    );
}
