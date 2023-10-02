import React from "react";
import TestResultUtils, { NodeTestResults, StateForSelectTestResults } from "../../../../common/TestResultUtils";
import { SelectNodeWithFocus } from "../../../withFocus";
import { css, cx } from "@emotion/css";
import { useNkTheme } from "../../../../containers/theme";
import { NodeRow } from "../NodeDetailsContent/NodeStyled";

export interface TestResultsSelectProps {
    results: NodeTestResults;
    value: string;
    onChange: (testResults?: StateForSelectTestResults) => void;
}

export default function TestResultsSelect(props: TestResultsSelectProps): JSX.Element {
    const { results, value, onChange } = props;

    const { theme } = useNkTheme();

    if (!TestResultUtils.hasTestResults(results)) {
        return null;
    }

    return (
        <NodeRow>
            <div
                className={cx(
                    "node-label",
                    css({
                        "&&&&": {
                            color: theme?.colors?.ok,
                        },
                    }),
                )}
            >
                Test case:
            </div>
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
        </NodeRow>
    );
}
