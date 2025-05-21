import { Box, styled } from "@mui/material";
import { useWindowManager } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React, { useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";

import { PanelSide } from "../../actions/nk";
import { moveToolbar, registerToolbars } from "../../actions/nk/toolbars";
import { useUserSettings } from "../../common/userSettings";
import { getCapabilities } from "../../reducers/selectors/other";
import { ToolbarsSide } from "../../reducers/toolbars";
import { WindowKind } from "../../windowManager";
import { SidePanel } from "../sidePanels/SidePanel";
import { SidePanelsContextProvider } from "../sidePanels/SidePanelsContext";
import { SidePanelToggleButton } from "../SidePanelToggleButton";
import { useSurvey } from "../toolbars/useSurvey";
import { DragAndDropContainer } from "./DragAndDropContainer";
import { Grid9 } from "./Grid9";
import { setInert } from "./inertHelpers";
import { Overlay } from "./Overlay";
import type { Toolbar } from "./toolbar";
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
    externalLayerWrapper?: React.ComponentType<PropsWithChildren>;
}>;

const AbsolutePanel = styled(Box)(({ theme }) => ({
    position: "absolute",
    inset: 0,
    zIndex: theme.zIndex.modal - 2, // elements using mui modal zIndex (e.g. menu, click outside mask) should be over our toolbar
    overflow: "hidden",
}));

function useShowFloatingToolbar() {
    const [settings] = useUserSettings();
    const { windows } = useWindowManager();
    const autoApply = settings["node.autoApply"];
    return useMemo(() => {
        if (!autoApply) return false;
        return windows.filter((w) => [WindowKind.editNode, WindowKind.viewNode].includes(w.kind)).length;
    }, [autoApply, windows]);
}

const ToolbarsLayer = (props: ToolbarsLayerProps): JSX.Element => {
    const dispatch = useDispatch();
    const { toolbars, configId, children, externalLayerWrapper: ExternalLayerWrapper = React.Fragment } = props;

    useEffect(() => {
        dispatch(registerToolbars(toolbars, configId));
    }, [dispatch, toolbars, configId]);

    const availableToolbars = useToolbarsVisibility(toolbars);

    const onMove = useCallback((from, to) => dispatch(moveToolbar(from, to, configId)), [configId, dispatch]);
    const showFloatingToolbar = useShowFloatingToolbar();

    return (
        <DragAndDropContainer onMove={onMove}>
            <ExternalLayerWrapper>
                <AbsoluteOverlayGrid9
                    m={0.5}
                    sx={(theme) => ({
                        transition: theme.transitions.create("top", { delay: showFloatingToolbar ? 750 : 0 }),
                        top: showFloatingToolbar ? 5 : -150,
                        justifyItems: "center",
                        overflow: "auto",
                    })}
                    ref={setInert(!showFloatingToolbar)}
                >
                    <StyledToolbarsContainer
                        sx={{ gridArea: "top" }}
                        availableToolbars={availableToolbars}
                        side={ToolbarsSide.AboveNodeWindow}
                        disableDnd
                    />
                </AbsoluteOverlayGrid9>
            </ExternalLayerWrapper>

            <SidePanelsContextProvider configId={configId}>
                <OverlayGrid9>
                    <Box gridArea="left" component={SidePanel} side={PanelSide.Left}>
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.LeftTop} />
                        <StyledToolbarsContainer availableToolbars={availableToolbars} side={ToolbarsSide.LeftBottom} />
                    </Box>

                    <StyledToolbarsContainer
                        sx={(theme) => ({
                            gridArea: "top",
                            opacity: showFloatingToolbar ? 0 : 1,
                            transition: theme.transitions.create("opacity", { delay: showFloatingToolbar ? 0 : 250 }),
                        })}
                        availableToolbars={availableToolbars}
                        side={ToolbarsSide.CenterTop}
                    />

                    <OverlayGrid9 gridArea="body" m={0.5}>
                        <Overlay gridArea="top/left / top/right" position="relative">
                            {children}
                        </Overlay>
                        <Box component={SidePanelToggleButton} type={PanelSide.Left} gridArea="bottom/left" />
                        <Box component={SidePanelToggleButton} type={PanelSide.Right} gridArea="bottom/right" />
                    </OverlayGrid9>

                    <StyledToolbarsContainer
                        sx={{ gridArea: "bottom" }}
                        availableToolbars={availableToolbars}
                        side={ToolbarsSide.CenterBottom}
                    />

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
export const AbsoluteOverlayGrid9 = AbsolutePanel.withComponent(OverlayGrid9);

const StyledToolbarsContainer = styled(ToolbarsContainer)(({ theme, side }) => {
    const padding = theme.spacing(0.25);
    switch (side) {
        case ToolbarsSide.LeftTop:
        case ToolbarsSide.RightTop:
            return { paddingTop: padding, paddingBottom: padding };
        case ToolbarsSide.LeftBottom:
        case ToolbarsSide.RightBottom:
            return { paddingTop: padding, paddingBottom: padding };
        default:
            return {
                padding: theme.spacing(0.5),
                flexDirection: "row",
                pointerEvents: "none",
                [`.${DRAGGABLE_LIST_CLASSNAME}`]: {
                    flexDirection: "row",
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
