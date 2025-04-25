import WarningIcon from "@mui/icons-material/Warning";
import { FormControl, FormHelperText, FormLabel } from "@mui/material";
import React from "react";

import { InfoTooltip } from "../editors/InfoTooltip";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { useTestResults } from "../TestResultsWrapper";

export default function TestErrors(): JSX.Element {
    const results = useTestResults();

    if (!results.testResultsToShow?.error) {
        return null;
    }

    return (
        <FormControl>
            <FormLabel>
                <InfoTooltip text={"Test case error"} variant={"hover"}>
                    <WarningIcon sx={(theme) => ({ color: theme.palette.warning.main })} />
                </InfoTooltip>
            </FormLabel>
            <div className={nodeValue}>
                <FormHelperText variant={"largeMessage"} error>
                    {results.testResultsToShow.error}
                </FormHelperText>
            </div>
        </FormControl>
    );
}
