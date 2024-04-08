import React from "react";
import { useSelector } from "react-redux";
import WarningIcon from "@mui/icons-material/Warning";
import { hasWarnings } from "../../reducers/selectors/graph";
import { IconWithLabel } from "../tips/IconWithLabel";
import { Typography } from "@mui/material";

function ProcessDialogWarnings(): JSX.Element {
    const processHasWarnings = useSelector(hasWarnings);
    return processHasWarnings ? (
        <Typography variant={"body2"} sx={(theme) => ({ color: theme.palette.warning.main, marginTop: "20px", marginBottom: "10px" })}>
            <IconWithLabel icon={WarningIcon} message={"Warnings found - please look at left panel to see details. Proceed with caution"} />
        </Typography>
    ) : null;
}

export default ProcessDialogWarnings;
