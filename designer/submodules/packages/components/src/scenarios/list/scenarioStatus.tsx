import React from "react";
import { NuIcon } from "../../common";
import { ProcessStateType } from "nussknackerUi/components/Process/types";
import { useTranslation } from "react-i18next";
import { Box, Typography } from "@mui/material";
import { capitalize, startCase } from "lodash";

interface Props {
    state: ProcessStateType;
}

export const ScenarioStatus = ({ state }: Props) => {
    const { t } = useTranslation();

    return (
        <Box fontSize={"1rem"} display={"flex"} gap={1}>
            <NuIcon
                title={t("scenario.iconTitle", "{{tooltip}}", {
                    context: state.status.name,
                    tooltip: state.tooltip,
                })}
                src={state.icon}
            />
            <Typography variant={"caption"}>{capitalize(startCase(state.status.name))}</Typography>
        </Box>
    );
};
