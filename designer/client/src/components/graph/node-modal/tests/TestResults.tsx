import { isEmpty, isObject, join } from "lodash";
import React from "react";
import InfoIcon from "@mui/icons-material/Info";
import NodeTip from "../NodeTip";
import { useTestResults } from "../TestResultsWrapper";
import { NodeId } from "../../../../types";
import { Box, FormControl, FormLabel, Link } from "@mui/material";
import { useTranslation } from "react-i18next";
import TestResultsVariables from "./TestResultsVariables";
import { Download } from "@mui/icons-material";

export default function TestResults({ nodeId }: { nodeId: NodeId }): JSX.Element {
    const { t } = useTranslation();
    const results = useTestResults();

    if (!results.testResultsToShow || isEmpty(results.testResultsToShow.context.variables)) {
        return null;
    }

    return (
        <Box sx={(theme) => ({ border: `1px solid ${theme.palette.success.main}`, padding: "5px" })}>
            <FormControl>
                <FormLabel>
                    <NodeTip title={"Variables in test case"} icon={<InfoIcon />} />
                </FormLabel>
            </FormControl>
            {Object.keys(results.testResultsToShow.context.variables)
                .sort((a, b) => a.localeCompare(b))
                .map((key, index) => (
                    <TestResultsVariables key={index} labelText={key} result={results.testResultsToShow.context.variables[key]} />
                ))}
            {results.testResultsToShow && !isEmpty(results.testResultsToShow.externalInvocationResultsForCurrentContext)
                ? results.testResultsToShow.externalInvocationResultsForCurrentContext.map((mockedValue, index) => (
                      <span key={index} className="testResultDownload">
                          <Link download={`${nodeId}-single-input.log`} href={downloadableHref(stringifyMockedValue(mockedValue))}>
                              <span className="glyphicon glyphicon-download" />
                              {t("testResults.resultsForThisInput", "Results for this input")}
                          </Link>
                      </span>
                  ))
                : null}
            {results.testResultsToShow && !isEmpty(results.testResultsToShow.externalInvocationResultsForEveryContext) ? (
                <Link
                    display={"flex"}
                    alignItems={"center"}
                    download={`${nodeId}-all-inputs.log`}
                    href={downloadableHref(mergedMockedResults(results.testResultsToShow.externalInvocationResultsForEveryContext))}
                >
                    <Download />
                    {t("testResults.resultsForAllInputs", "Results for all inputs")}
                </Link>
            ) : null}
        </Box>
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
