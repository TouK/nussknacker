import i18next from "i18next";
import React, { memo } from "react";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarButtons } from "../../toolbarComponents/toolbarButtons";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ProcessStateIcon from "../../Process/ProcessStateIcon";
import { Box, Typography } from "@mui/material";
import ProcessStateUtils from "../../Process/ProcessStateUtils";
import { getScenario } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";

const ProcessActions = memo(({ buttonsVariant, children, ...props }: ToolbarPanelProps) => {
    const scenario = useSelector((state: RootState) => getScenario(state));
    const processState = useSelector((state: RootState) => getProcessState(state));

    // TODO: better styling of process info toolbar in case of many custom actions

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioActions.title", "Scenario actions")}>
            {!scenario.isFragment && (
                <Box display={"flex"} px={2} pt={2} pb={1.5}>
                    <ProcessStateIcon scenario={scenario} processState={processState} />
                    <Typography component={"div"} variant={"body2"} pl={1}>
                        {ProcessStateUtils.getStateDescription(scenario, processState)}
                    </Typography>
                </Box>
            )}
            <ToolbarButtons variant={buttonsVariant}>{children}</ToolbarButtons>
        </ToolbarWrapper>
    );
});

ProcessActions.displayName = "customActions";

export default ProcessActions;
