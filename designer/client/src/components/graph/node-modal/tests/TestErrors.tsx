import WarningIcon from "@mui/icons-material/Warning";
import { FormHelperText } from "@mui/material";
import React from "react";

import { FormControl, FormLabel } from "../editors/FormControl";
import { InfoTooltip } from "../editors/InfoTooltip/InfoTooltip";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { useTestResults } from "../TestResultsWrapper";

export default function TestErrors(): React.JSX.Element {
    const results = useTestResults();

    if (!results.testResultsToShow?.error) {
        return null;
    }

    return (
        <FormControl>
            <FormLabel>
                <InfoTooltip title={"Error during evaluation"} variant={"hover"}>
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
