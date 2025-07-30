import type { PropsWithChildren } from "react";
import React, { createContext, useContext, useMemo, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

import { PanelSide } from "../../actions/nk/ui/panelSide";
import { panelsState } from "../../reducers/selectors/panel";

const SideContext = createContext<PanelSide>(null);

export function SideContextProvider({ side, children }: PropsWithChildren<{ side: PanelSide }>) {
    return <SideContext.Provider value={side}>{children}</SideContext.Provider>;
}

type SideState = {
    side: PanelSide;
    isOpened: boolean;
    isBackground?: boolean;
    toggleCollapse: () => void;
    toggleFullSize: (fullSize: boolean) => void;
    switchVisible: boolean;
    ref: React.MutableRefObject<HTMLDivElement>;
};

const SidePanelsContext = createContext<Record<PanelSide, SideState>>(null);

function useSideState(configId: string, side: PanelSide): SideState {
    const dispatch = useDispatch();
    const state = useSelector(panelsState);
    const [fullSize, setFullSize] = useState(true);
    const ref = useRef<HTMLDivElement>();

    return useMemo(
        () => ({
            side,
            isOpened: state[side],
            isBackground:
                (side === PanelSide.Left && state[PanelSide.LeftDynamic]) || (side === PanelSide.Right && state[PanelSide.RightDynamic]),
            switchVisible: !state[side] || fullSize,
            toggleCollapse: () => {
                dispatch({
                    type: "TOGGLE_PANEL",
                    configId,
                    side,
                });
            },
            toggleFullSize: setFullSize,
            ref,
        }),
        [configId, dispatch, fullSize, side, state],
    );
}

export function SidePanelsContextProvider({ configId, children }: PropsWithChildren<{ configId: string }>) {
    const leftState = useSideState(configId, PanelSide.Left);
    const rightState = useSideState(configId, PanelSide.Right);
    const leftDynamicState = useSideState(configId, PanelSide.LeftDynamic);
    const rightDynamicState = useSideState(configId, PanelSide.RightDynamic);

    const value = useMemo(
        () => ({
            [PanelSide.Left]: leftState,
            [PanelSide.Right]: rightState,
            [PanelSide.LeftDynamic]: leftDynamicState,
            [PanelSide.RightDynamic]: rightDynamicState,
        }),
        [leftDynamicState, leftState, rightDynamicState, rightState],
    );

    return <SidePanelsContext.Provider value={value}>{children}</SidePanelsContext.Provider>;
}

export function useSidePanel(side?: PanelSide) {
    const panelsContext = useContext(SidePanelsContext);
    const sideContext = useContext(SideContext);

    if (!panelsContext) {
        throw new Error(`${useSidePanel.name} was used outside of SidePanelsContext provider`);
    }

    if (side) return panelsContext[side];

    if (sideContext) return panelsContext[sideContext];

    throw new Error(`${useSidePanel.name} was used outside of SideContext provider`);
}
