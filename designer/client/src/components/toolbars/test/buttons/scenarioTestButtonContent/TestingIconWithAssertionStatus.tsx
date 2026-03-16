import { Box } from "@mui/material";
import React from "react";

import TestingIcon from "../../../../../assets/img/toolbarButtons/test.svg";
import { AssertionStatusIcon } from "../../../testCases/assertionResultsForNode/assertionResult/AssertionStatusIcon";

interface Props {
    hasResult: boolean;
    assertionsIsSuccess: boolean;
}

export const TestingIconWithAssertionStatus = ({ hasResult, assertionsIsSuccess }: Props) => (
    <Box
        component="span"
        sx={{
            position: "relative",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
        }}
    >
        <TestingIcon />
        {hasResult && (
            <Box
                component="span"
                sx={{
                    position: "absolute",
                    top: 0,
                    left: -2,
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    pointerEvents: "none",
                }}
            >
                <AssertionStatusIcon isSuccess={assertionsIsSuccess} variant={"dark"} />
            </Box>
        )}
    </Box>
);
