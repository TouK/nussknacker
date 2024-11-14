import React, { PropsWithChildren, useCallback, useEffect, useMemo } from "react";
import { ToolbarsSide } from "../../reducers/toolbars";
import { useDispatch, useSelector } from "react-redux";
import { moveToolbar, registerToolbars } from "../../actions/nk/toolbars";
import { ToolbarsContainer } from "./ToolbarsContainer";
import { SidePanel } from "../sidePanels/SidePanel";
import { Toolbar } from "./toolbar";
import { getCapabilities } from "../../reducers/selectors/other";
import { useSurvey } from "../toolbars/useSurvey";
import { DragAndDropContainer } from "./DragAndDropContainer";
import { Box, styled } from "@mui/material";
import { Overlay } from "./Overlay";
import { Grid9 } from "./Grid9";
import { PanelSide } from "../../actions/nk";
import { SidePanelToggleButton } from "../SidePanelToggleButton";
import { SidePanelsContextProvider } from "../sidePanels/SidePanelsContext";

export function useToolbarsVisibility(toolbars: Toolbar[]) {
    const { editFrontend } = useSelector(getCapabilities);
    const [showSurvey] = useSurvey();

    const hiddenToolbars = useMemo(
        () => ({
            "survey-panel": !showSurvey,
            "creator-panel": !editFrontend,
        }),
        [editFrontend, showSurvey],
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
                        <Box
                            component={SidePanelToggleButton}
                            type={PanelSide.Left}
                            gridArea="bottom/left"
                            handleStyle={(isOpened) => ({ marginLeft: isOpened ? "unset" : "32px" })}
                        />
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
