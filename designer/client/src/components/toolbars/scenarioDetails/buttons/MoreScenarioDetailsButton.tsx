import React from "react";
import { Link, Typography } from "@mui/material";
import { useWindows, WindowKind } from "../../../../windowManager";
import i18next from "i18next";
import { ProcessStateType, Scenario } from "../../../Process/types";
import { EventTrackingSelector, EventTrackingType, getEventTrackingProps } from "../../../../containers/event-tracking";

interface Props {
    scenario: Scenario;
    processState: ProcessStateType;
}

export const MoreScenarioDetailsButton = ({ scenario, processState }: Props) => {
    const { open } = useWindows();
    return (
        <Typography
            component={Link}
            variant={"overline"}
            color={"text"}
            sx={(theme) => ({ cursor: "pointer", textDecorationColor: theme.palette.text.secondary, py: 0.5 })}
            onClick={() =>
                open({
                    kind: WindowKind.scenarioDetails,
                    meta: { scenario, processState },
                })
            }
            {...getEventTrackingProps({ selector: EventTrackingSelector.MoreScenarioDetails, event: EventTrackingType.CLICK })}
        >
            {i18next.t("panels.scenarioDetails.moreButton", "More scenario details")}
        </Typography>
    );
};
