import { Box, Typography } from "@mui/material";
import { capitalize, startCase } from "lodash";
import React from "react";
import { useTranslation } from "react-i18next";

import { NuIcon } from "../../common/nuIcon";

interface Props {
    value: string;
    tooltip: string;
    icon: string;
}
export const ScenarioStatusFormatted = ({ value, tooltip, icon }: Props) => {
    const { t } = useTranslation();

    return (
        <Box display={"flex"} gap={1} fontSize={"1rem"} alignItems={"center"}>
            <NuIcon
                title={t("scenario.iconTitle", "{{tooltip}}", {
                    context: value,
                    tooltip,
                })}
                src={icon}
            />
            <Typography variant={"caption"}>{capitalize(startCase(value))}</Typography>
        </Box>
    );
};
