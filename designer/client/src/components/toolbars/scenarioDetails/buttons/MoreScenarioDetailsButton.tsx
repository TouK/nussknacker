import React from "react";
import { Link, Typography } from "@mui/material";
import { useWindows, WindowKind } from "../../../../windowManager";
import i18next from "i18next";
import { ProcessStateType, Scenario } from "../../../Process/types";
import { EventTrackingSelector, getEventTrackingProps } from "../../../../containers/event-tracking";

interface Props {
    scenario: Scenario;
    processState: ProcessStateType;
}

export const MoreScenarioDetailsButton = ({ scenario, processState }: Props) => {
    const { open } = useWindows();
    return (
        <Typography
            component={Link}
            variant={"caption"}
            sx={(theme) => ({ cursor: "pointer", color: theme.palette.primary.main, py: 0.5, textDecoration: "none" })}
            onClick={() =>
                open({
                    kind: WindowKind.scenarioDetails,
                    meta: { scenario, processState },
                })
            }
            {...getEventTrackingProps({ selector: EventTrackingSelector.MoreScenarioDetails })}
        >
            {i18next.t("panels.scenarioDetails.moreButton", "More details")}
        </Typography>
    );
};
