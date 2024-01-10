import React from "react";
import { ProcessStateType, Scenario } from "./types";
import ProcessStateUtils from "./ScenarioStateUtils";
import UrlIcon from "../UrlIcon";
import { Box, Divider, Popover, Typography } from "@mui/material";
import { Errors } from "./ProcessErrors";

interface Props {
    processState?: ProcessStateType;
    process: Scenario;
}

function ProcessStateIcon({ process, processState }: Props) {
    const icon = ProcessStateUtils.getStatusIcon(process, processState);
    const tooltip = ProcessStateUtils.getStatusTooltip(process, processState);
    const [anchorEl, setAnchorEl] = React.useState<HTMLButtonElement | null>(null);

    return (
        <>
            <UrlIcon src={icon} title={tooltip} onClick={(event) => processState && setAnchorEl(event.currentTarget)} />
            <Popover
                anchorEl={anchorEl}
                anchorOrigin={{
                    vertical: "center",
                    horizontal: "left",
                }}
                transformOrigin={{
                    vertical: "center",
                    horizontal: "right",
                }}
                onClose={() => setAnchorEl(null)}
                open={!!anchorEl}
            >
                <Typography p={1} variant="h6">
                    {process.name}
                </Typography>
                <Divider />
                <Box p={1}>
                    <Typography variant="body2" style={{ whiteSpace: "pre-wrap" }}>
                        {tooltip}
                    </Typography>
                    <Errors state={processState} />
                </Box>
            </Popover>
        </>
    );
}

export default ProcessStateIcon;
