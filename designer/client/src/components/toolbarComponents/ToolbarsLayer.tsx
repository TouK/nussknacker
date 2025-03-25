import React, { PropsWithChildren, useCallback, useEffect, useMemo } from "react";
import { useUserSettings } from "../../common/userSettings";
import { ToolbarsSide } from "../../reducers/toolbars";
import { Box, Stack, styled } from "@mui/material";
import React, { ComponentType, Fragment, PropsWithChildren, useCallback, useEffect, useMemo } from "react";
import { Box, styled } from "@mui/material";
import React, { PropsWithChildren, useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { PanelSide } from "../../actions/nk";
import { moveToolbar, registerToolbars } from "../../actions/nk/toolbars";
import { getCapabilities } from "../../reducers/selectors/other";
import { ToolbarsSide } from "../../reducers/toolbars";
import { SidePanel } from "../sidePanels/SidePanel";
import { SidePanelsContextProvider } from "../sidePanels/SidePanelsContext";
import { SidePanelToggleButton } from "../SidePanelToggleButton";
import { useSurvey } from "../toolbars/useSurvey";
import { DragAndDropContainer } from "./DragAndDropContainer";
import { Grid9 } from "./Grid9";
import { Overlay } from "./Overlay";
import { Toolbar } from "./toolbar";
import { DRAGGABLE_LIST_CLASSNAME, ToolbarsContainer } from "./ToolbarsContainer";

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

const AbsolutePanel = styled(Box)(({ theme }) => ({
    position: "absolute",
    zIndex: theme.zIndex.appBar,
    overflowY: "hidden",
    overflowX: "auto",
    display: "flex",
    alignItems: "center",
    pointerEvents: "none",
}));

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
            <AbsolutePanel sx={(t) => ({ top: t.spacing(1), left: t.spacing(1), right: t.spacing(1) })}>
                <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.TopCenter} />
            </AbsolutePanel>

            <AbsolutePanel sx={(t) => ({ bottom: t.spacing(1), left: t.spacing(1), right: t.spacing(1) })}>
                <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.BottomCenter} />
            </AbsolutePanel>

            <SidePanelsContextProvider configId={configId}>
                <OverlayGrid9>
                    <Box gridArea="left" component={SidePanel} side={PanelSide.Left}>
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.LeftTop} />
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.LeftBottom} />
                    </Box>

                    <OverlayGrid9 gridArea="body" m={0.5}>
                        <Overlay gridArea="top/left / top/right" position="relative">
                            {children}
                        </Overlay>
                        <Box component={SidePanelToggleButton} type={PanelSide.Left} gridArea="bottom/left" />
                        <Box component={SidePanelToggleButton} type={PanelSide.Right} gridArea="bottom/right" />
                    </OverlayGrid9>

                    <Box gridArea="right" component={SidePanel} side={PanelSide.Right}>
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.RightTop} />
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.RightBottom} />
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
        case ToolbarsSide.LeftTop:
        case ToolbarsSide.RightTop:
            return { paddingBottom: padding };
        case ToolbarsSide.LeftBottom:
        case ToolbarsSide.RightBottom:
            return { paddingTop: padding };
        default:
            return {
                padding: 0,
                flexDirection: "row",
                pointerEvents: "none",
                [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
                    flexDirection: "row",
                    margin: 1,
                    gap: 1,
                    minWidth: 100,
                    "&>*": {
                        pointerEvents: "all",
                    },
                },
            };
    }
});

export default ToolbarsLayer;
