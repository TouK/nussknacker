import React from "react";
import TestResultUtils, { NodeTestResults, StateForSelectTestResults } from "../../../../common/TestResultUtils";
import { SelectNodeWithFocus } from "../../../withFocus";
import { css, cx } from "@emotion/css";
import { FormControl, FormLabel, useTheme } from "@mui/material";

export interface TestResultsSelectProps {
    results: NodeTestResults;
    value: string;
    onChange: (testResults?: StateForSelectTestResults) => void;
}

export default function TestResultsSelect(props: TestResultsSelectProps): JSX.Element {
    const { results, value, onChange } = props;

    const theme = useTheme();

    if (!TestResultUtils.hasTestResults(results)) {
        return null;
    }

    return (
        <FormControl>
            <FormLabel
                className={cx(
                    css({
                        "&&&&": {
                            color: theme.custom.colors.ok,
                        },
                    }),
                )}
            >
                Test case:
            </FormLabel>
            <div className="node-value">
                <SelectNodeWithFocus
                    className="node-input selectResults"
                    onChange={(e) => onChange(TestResultUtils.stateForSelectTestResults(results, e.target.value))}
                    value={value}
                >
                    {TestResultUtils.availableContexts(results).map(({ display, id }) => (
                        <option key={id} value={id}>
                            {id} ({display})
                        </option>
                    ))}
                </SelectNodeWithFocus>
            </div>
        </FormControl>
    );
}
