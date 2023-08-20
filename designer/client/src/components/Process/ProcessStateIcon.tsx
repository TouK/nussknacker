import React from "react";
import { useTranslation } from "react-i18next";
import { ProcessStateType, ProcessType } from "./types";
import ProcessStateUtils from "./ProcessStateUtils";
import UrlIcon from "../UrlIcon";
import { Box, Divider, Popover, Typography } from "@mui/material";

interface Props {
    processState?: ProcessStateType;
    process: ProcessType;
}

function Errors({ state }: { state: ProcessStateType }) {
    const { t } = useTranslation();

    if (state.errors?.length < 1) {
        return null;
    }

    return (
        <div>
            <span>{t("stateIcon.errors", "Errors:")}</span>
            <ul>
                {state.errors.map((error, key) => (
                    <li key={key}>{error}</li>
                ))}
            </ul>
        </div>
    );
}

function ProcessStateIcon({ process, processState }: Props) {
    const icon = ProcessStateUtils.getStatusIcon(process, processState);
    const tooltip = ProcessStateUtils.getStatusTooltip(process, processState);

    const [anchorEl, setAnchorEl] = React.useState<HTMLButtonElement | null>(null);

    return (
        <>
            <UrlIcon src={icon} title={tooltip} onClick={(event) => setAnchorEl(event.currentTarget)} />
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
                    <Typography variant="body2">{tooltip}</Typography>
                    <Errors state={processState} />
                </Box>
            </Popover>
        </>
    );
}

export default ProcessStateIcon;
