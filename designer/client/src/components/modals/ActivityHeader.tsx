import { useSelector } from "react-redux";
import { getProcessName } from "../../reducers/selectors/graph";
import { Box, Typography } from "@mui/material";
import React from "react";
import ProcessDialogWarnings from "./ProcessDialogWarnings";

interface Props {
    title: string;
    displayWarnings?: boolean;
}

export function ActivityHeader(props: Props): JSX.Element {
    const processName = useSelector(getProcessName);
    return (
        <Box>
            <Typography variant={"inherit"} sx={{ width: "100%", "::after": { content: "':'" } }}>
                {props.title}
            </Typography>
            <Typography
                variant={"h5"}
                sx={{
                    width: "100%",
                    fontWeight: "bold",
                    margin: 0,
                    lineHeight: "2em",
                }}
            >
                {processName}
            </Typography>
            {props.displayWarnings && <ProcessDialogWarnings />}
        </Box>
    );
}
