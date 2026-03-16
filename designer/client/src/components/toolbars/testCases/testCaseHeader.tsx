import { Box, Typography } from "@mui/material";
import React from "react";

import { getActiveTestCase } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";

export const TestCaseHeader = () => {
    const testCase = useAppSelector(getActiveTestCase);

    return (
        <Box display={"flex"} mx={1} mb={1}>
            <Typography variant={"body1"} color={"text.primary"}>
                {testCase.name}
            </Typography>
        </Box>
    );
};
