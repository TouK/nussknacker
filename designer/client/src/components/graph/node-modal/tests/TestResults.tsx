import { isEmpty, isObject, join } from "lodash";
import React from "react";
import InfoIcon from "@mui/icons-material/Info";
import NodeTip from "../NodeTip";
import TestValue from "./TestValue";
import { useTestResults } from "../TestResultsWrapper";
import { NodeId } from "../../../../types";
import { NodeTableBody } from "../NodeDetailsContent/NodeTable";
import { NodeLabelStyled } from "../node";
import { NodeRow } from "../NodeDetailsContent/NodeStyled";

export default function TestResults({ nodeId }: { nodeId: NodeId }): JSX.Element {
    const results = useTestResults();

    if (!results.testResultsToShow || isEmpty(results.testResultsToShow.context.variables)) {
        return null;
    }

    return (
        <NodeTableBody className="node-test-results">
            <NodeRow>
                <NodeLabelStyled>
                    <NodeTip
                        title={"Variables in test case"}
                        icon={<InfoIcon sx={(theme) => ({ color: theme.custom.colors.info, alignSelf: "center" })} />}
                    />
                </NodeLabelStyled>
            </NodeRow>
            {Object.keys(results.testResultsToShow.context.variables)
                .sort((a, b) => a.localeCompare(b))
                .map((key, ikey) => (
                    <NodeRow key={ikey}>
                        <div className="node-label">{key}:</div>
                        <TestValue value={results.testResultsToShow.context.variables[key]} shouldHideTestResults={false} />
                    </NodeRow>
                ))}
            {results.testResultsToShow && !isEmpty(results.testResultsToShow.externalInvocationResultsForCurrentContext)
                ? results.testResultsToShow.externalInvocationResultsForCurrentContext.map((mockedValue, index) => (
                      <span key={index} className="testResultDownload">
                          <a download={`${nodeId}-single-input.log`} href={downloadableHref(stringifyMockedValue(mockedValue))}>
                              <span className="glyphicon glyphicon-download" /> Results for this input
                          </a>
                      </span>
                  ))
                : null}
            {results.testResultsToShow && !isEmpty(results.testResultsToShow.externalInvocationResultsForEveryContext) ? (
                <span className="testResultDownload">
                    <a
                        download={`${nodeId}-all-inputs.log`}
                        href={downloadableHref(mergedMockedResults(results.testResultsToShow.externalInvocationResultsForEveryContext))}
                    >
                        <span className="glyphicon glyphicon-download" /> Results for all inputs
                    </a>
                </span>
            ) : null}
        </NodeTableBody>
    );

    function mergedMockedResults(mockedResults) {
        return join(
            mockedResults.map((mockedValue) => stringifyMockedValue(mockedValue)),
            "\n\n",
        );
    }

    function downloadableHref(content) {
        return `data:application/octet-stream;charset=utf-8,${encodeURIComponent(content)}`;
    }

    function stringifyMockedValue(mockedValue) {
        const content = mockedValue.value?.pretty;
        return isObject(content) ? JSON.stringify(content) : content;
    }
}
