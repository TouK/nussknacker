import { Box, styled } from "@mui/material";
import type { PropsWithChildren} from "react";
import React, { useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";

import { PanelSide } from "../../actions/nk";
import { moveToolbar, registerToolbars } from "../../actions/nk/toolbars";
import { useUserSettings } from "../../common/userSettings";
import { getCapabilities } from "../../reducers/selectors/other";
import { ToolbarsSide } from "../../reducers/toolbars";
import { SidePanel } from "../sidePanels/SidePanel";
import { SidePanelsContextProvider } from "../sidePanels/SidePanelsContext";
import { SidePanelToggleButton } from "../SidePanelToggleButton";
import { useSurvey } from "../toolbars/useSurvey";
import { DragAndDropContainer } from "./DragAndDropContainer";
import { Overlay } from "./Overlay";
import type { Toolbar } from "./toolbar";
import { ToolbarsContainer } from "./ToolbarsContainer";
import { Grid9 } from "./Grid9";

export function useToolbarsVisibility(toolbars: Toolbar[]) {
    const { editFrontend } = useSelector(getCapabilities);
    const [showSurvey] = useSurvey();
    const [userSettings] = useUserSettings();

    const hiddenToolbars = useMemo<Record<string, boolean>>(
        () => ({
            "survey-panel": !showSurvey,
            "creator-panel": !editFrontend,
            "user-settings-panel": !userSettings["debug.userSettingsVisible"],
        }),
        [editFrontend, showSurvey, userSettings],
    );

    return useMemo(
        () =>
            toolbars.map((t) => ({
                ...t,
                isHidden: hiddenToolbars[t.id],
            })),
        [hiddenToolbars, toolbars],
    );
}

type ToolbarsLayerProps = PropsWithChildren<{
    toolbars: Toolbar[];
    configId: string;
}>;

const ToolbarsLayer = (props: ToolbarsLayerProps): JSX.Element => {
    const dispatch = useDispatch();
    const { toolbars, configId, children } = props;

    useEffect(() => {
        dispatch(registerToolbars(toolbars, configId));
    }, [dispatch, toolbars, configId]);

    const availableToolbars = useToolbarsVisibility(toolbars);

    const onMove = useCallback((from, to) => dispatch(moveToolbar(from, to, configId)), [configId, dispatch]);

    return (
        <DragAndDropContainer onMove={onMove}>
            <SidePanelsContextProvider configId={configId}>
                <OverlayGrid9>
                    <Box gridArea="left" component={SidePanel} side={PanelSide.Left}>
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopLeft} />
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomLeft} />
                    </Box>

                    <OverlayGrid9 gridArea="body" m={0.5}>
                        <Overlay gridArea="top/left / top/right" position="relative">
                            {children}
                        </Overlay>
                        <Box component={SidePanelToggleButton} type={PanelSide.Left} gridArea="bottom/left" />
                        <Box component={SidePanelToggleButton} type={PanelSide.Right} gridArea="bottom/right" />
                    </OverlayGrid9>

                    <Box gridArea="right" component={SidePanel} side={PanelSide.Right}>
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopRight} />
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomRight} />
                    </Box>
                </OverlayGrid9>
            </SidePanelsContextProvider>
        </DragAndDropContainer>
    );
};

export const OverlayGrid9 = Overlay.withComponent(Grid9);

const StyledToolbarsContainer = styled(ToolbarsContainer)(({ theme, side }) => {
    const padding = `calc(${theme.spacing(0.375)} / 2)`;
    switch (side) {
        case ToolbarsSide.TopLeft:
        case ToolbarsSide.TopRight:
            return { paddingBottom: padding };
        case ToolbarsSide.BottomLeft:
        case ToolbarsSide.BottomRight:
            return { paddingTop: padding };
        default:
            return {
                paddingTop: padding,
                paddingBottom: padding,
            };
    }
});

export default ToolbarsLayer;
