import WarningIcon from "@mui/icons-material/Warning";
import { Typography } from "@mui/material";
import React from "react";

import { hasWarnings } from "../../reducers/selectors/graph2";
import { useAppSelector } from "../../store/storeHelpers";
import { IconWithLabel } from "../tips/IconWithLabel";

function ProcessDialogWarnings(): React.JSX.Element {
    const processHasWarnings = useAppSelector(hasWarnings);
    return processHasWarnings ? (
        <Typography variant={"body2"} sx={(theme) => ({ color: theme.palette.warning.main, marginTop: "20px", marginBottom: "10px" })}>
            <IconWithLabel icon={WarningIcon} message={"Warnings found - please look at left panel to see details. Proceed with caution"} />
        </Typography>
    ) : null;
}

export default ProcessDialogWarnings;
