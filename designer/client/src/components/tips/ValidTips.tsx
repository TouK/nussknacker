import React from "react";
import TestingMode from "../../assets/img/icons/testingMode.svg";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ValidTip from "./ValidTip";
import { Box, CircularProgress, useTheme } from "@mui/material";

export default function ValidTips(props: { loading: boolean; hasNeitherErrorsNorWarnings?: boolean; testing?: boolean }): JSX.Element {
    const { loading, hasNeitherErrorsNorWarnings, testing } = props;
    const theme = useTheme();

    return (
        <React.Fragment>
            {loading && (
                <Box display={"flex"} justifyContent={"center"} height={"100%"} alignItems={"center"}>
                    <CircularProgress />
                </Box>
            )}
            {hasNeitherErrorsNorWarnings && (
                <ValidTip icon={CheckCircleIcon} message={"Everything seems to be OK"} color={theme.palette.success.main} />
            )}
            {testing && <ValidTip icon={TestingMode} message={"Testing mode enabled"} color={"inherit"} />}
        </React.Fragment>
    );
}
