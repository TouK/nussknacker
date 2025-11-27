import React from "react";

import BranchParameters from "./BranchParameters";
import type { CustomNodeProps } from "./customNode";
import { CustomNode } from "./customNode";
import { useTestResults } from "./TestResultsWrapper";

export function JoinNode(props: CustomNodeProps): React.JSX.Element {
    const { errors, isEditMode, node, parameterDefinitions, setProperty, showSwitch, showValidation } = props;
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
            />
        </CustomNode>
    );
}
