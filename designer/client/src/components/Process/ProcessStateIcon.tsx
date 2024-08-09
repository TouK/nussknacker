import React from "react";
import { ProcessStateType, Scenario } from "./types";
import ProcessStateUtils from "./ProcessStateUtils";
import UrlIcon from "../UrlIcon";
import { Box, Divider, Popover, styled, Typography } from "@mui/material";
import { Errors } from "./ProcessErrors";

const StyledUrlIcon = styled(UrlIcon)(({ theme }) => ({
    width: theme.spacing(2.5),
    height: theme.spacing(2.5),
}));

interface Props {
    processState?: ProcessStateType;
    scenario: Scenario;
}

function ProcessStateIcon({ scenario, processState }: Props) {
    const icon = ProcessStateUtils.getStatusIcon(scenario, processState);
    const tooltip = ProcessStateUtils.getStatusTooltip(scenario, processState);
    const [anchorEl, setAnchorEl] = React.useState<HTMLButtonElement | null>(null);

    return (
        <>
            <StyledUrlIcon src={icon} title={tooltip} onClick={(event) => processState && setAnchorEl(event.currentTarget)} />
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
                <Typography p={1} variant="body1">
                    {scenario.name}
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
