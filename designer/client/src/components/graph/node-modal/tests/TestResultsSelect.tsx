import { css, cx } from "@emotion/css";
import { useTheme } from "@mui/material";
import React from "react";

import type { NodeTestResults, StateForSelectTestResults } from "../../../../common/TestResultUtils";
import TestResultUtils from "../../../../common/TestResultUtils";
import { FormControl, FormLabel } from "../editors/FormControl";
import type { Option } from "../fragment-input-definition/TypeSelect";
import { TypeSelect } from "../fragment-input-definition/TypeSelect";

export interface TestResultsSelectProps {
    results: NodeTestResults;
    value: string;
    onChange: (testResults?: StateForSelectTestResults) => void;
}

export default function TestResultsSelect(props: TestResultsSelectProps): React.JSX.Element {
    const { results, value, onChange } = props;

    const theme = useTheme();

    if (!TestResultUtils.hasTestResults(results)) {
        return null;
    }

    const availableContexts: Option[] = TestResultUtils.availableContexts(results).map(({ id, display }) => ({
        label: `${id} ${display}`,
        value: id,
    }));

    return (
        <FormControl>
            <FormLabel
                className={cx(
                    css({
                        "&&&&": {
                            color: theme.palette.success.main,
                        },
                    }),
                )}
            >
                Test case:
            </FormLabel>
            <TypeSelect
                className="selectResults"
                onChange={(value) => onChange(TestResultUtils.stateForSelectTestResults(results, value))}
                options={availableContexts}
                value={availableContexts.find((availableContext) => availableContext.value === value)}
                fieldErrors={[]}
            />
        </FormControl>
    );
}
