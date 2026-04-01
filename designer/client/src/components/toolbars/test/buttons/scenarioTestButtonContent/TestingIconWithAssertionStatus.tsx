import { Box } from "@mui/material";
import React from "react";

import TestingIcon from "../../../../../assets/img/toolbarButtons/test.svg";
import type { AssertionStatus } from "../../../testCases/assertionResultsForNode/assertionResult/AssertionStatusIcon";
import { AssertionStatusIcon } from "../../../testCases/assertionResultsForNode/assertionResult/AssertionStatusIcon";

interface Props {
    status: AssertionStatus | null;
}

export const TestingIconWithAssertionStatus = ({ status }: Props) => (
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
        {status && (
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
                <AssertionStatusIcon status={status} variant={"dark"} />
            </Box>
        )}
    </Box>
);
