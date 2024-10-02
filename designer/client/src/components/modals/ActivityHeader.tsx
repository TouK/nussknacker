import { useSelector } from "react-redux";
import { getProcessName } from "../../reducers/selectors/graph";
import { Typography } from "@mui/material";
import React from "react";
import ProcessDialogWarnings from "./ProcessDialogWarnings";

interface Props {
    title: string;
    displayWarnings?: boolean;
}

export function ActivityHeader(props: Props): JSX.Element {
    const processName = useSelector(getProcessName);
    return (
        <>
            <Typography
                variant={"body2"}
                sx={{ width: "100%", "::after": { content: "':'" }, fontWeight: 600, fontSize: "16px", lineHeight: "24px" }}
            >
                {props.title}
            </Typography>
            <Typography variant={"body2"} sx={{ width: "100%", fontWeight: 600, fontSize: "20px", lineHeight: "30px" }}>
                {processName}
            </Typography>
            {props.displayWarnings && <ProcessDialogWarnings />}
        </>
    );
}
