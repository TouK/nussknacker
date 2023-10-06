import React, { PropsWithChildren, useState } from "react";
import InfoIcon from "@mui/icons-material/Info";
import NodeTip from "../NodeTip";
import TestValue from "./TestValue";
import { NodeResultsForContext } from "../../../../common/TestResultUtils";
import { variables } from "../../../../stylesheets/variables";
import { NodeRow } from "../NodeDetailsContent/NodeStyled";

interface ExpressionTestResultsProps {
    fieldName: string;
    resultsToShow: NodeResultsForContext;
}

export default function ExpressionTestResults(props: PropsWithChildren<ExpressionTestResultsProps>): JSX.Element {
    const { fieldName, resultsToShow } = props;
    const [hideTestResults, toggleTestResults] = useState(false);

    const testValue = fieldName ? resultsToShow && resultsToShow.expressionResults[fieldName] : null;
    const showIconClass = hideTestResults ? "glyphicon glyphicon-eye-close" : "glyphicon glyphicon-eye-open";

    return testValue ? (
        <div>
            {props.children}
            <NodeRow className="node-test-results">
                <div className="node-label">
                    <NodeTip
                        title={"Value evaluated in test case"}
                        icon={<InfoIcon sx={(theme) => ({ color: theme.custom.colors.info, alignSelf: "center" })} />}
                    />
                    {testValue.pretty ? <span className={showIconClass} onClick={() => toggleTestResults((s) => !s)} /> : null}
                </div>
                <TestValue value={testValue} shouldHideTestResults={hideTestResults} />
            </NodeRow>
        </div>
    ) : (
        <>{props.children}</>
    );
}
