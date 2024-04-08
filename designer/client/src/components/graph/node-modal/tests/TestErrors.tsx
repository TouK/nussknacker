import React from "react";
import WarningIcon from "@mui/icons-material/Warning";
import NodeTip from "../NodeTip";
import { useTestResults } from "../TestResultsWrapper";
import { FormControl, FormHelperText, FormLabel } from "@mui/material";

export default function TestErrors(): JSX.Element {
    const results = useTestResults();

    if (!results.testResultsToShow?.error) {
        return null;
    }

    return (
        <FormControl>
            <FormLabel>
                <NodeTip title={"Test case error"} icon={<WarningIcon sx={(theme) => ({ color: theme.palette.warning.main })} />} />
            </FormLabel>
            <div className="node-value">
                <FormHelperText variant={"largeMessage"} error>
                    {results.testResultsToShow.error}
                </FormHelperText>
            </div>
        </FormControl>
    );
}
