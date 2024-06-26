import React, { createContext, PropsWithChildren, useContext, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { panelsState } from "../../reducers/selectors/panel";
import { PanelSide } from "../../actions/nk";

const SideContext = createContext<PanelSide>(null);

export function SideContextProvider({ side, children }: PropsWithChildren<{ side: PanelSide }>) {
    return <SideContext.Provider value={side}>{children}</SideContext.Provider>;
}

type SideState = {
    isOpened: boolean;
    toggleCollapse: () => void;
    toggleFullSize: (fullSize: boolean) => void;
    switchVisible: boolean;
};

const SidePanelsContext = createContext<Record<PanelSide, SideState>>(null);

function useSideState(configId: string, side: PanelSide) {
    const dispatch = useDispatch();
    const state = useSelector(panelsState);
    const [fullSize, setFullSize] = useState(true);

    return useMemo(
        () => ({
            isOpened: state[side],
            switchVisible: !state[side] || fullSize,
            toggleCollapse: () => {
                dispatch({
                    type: "TOGGLE_PANEL",
                    configId,
                    side,
                });
            },
            toggleFullSize: setFullSize,
        }),
        [configId, dispatch, fullSize, side, state],
    );
}

export function SidePanelsContextProvider({ configId, children }: PropsWithChildren<{ configId: string }>) {
    const leftState = useSideState(configId, PanelSide.Left);
    const rightState = useSideState(configId, PanelSide.Right);

    const value = useMemo(
        () => ({
            [PanelSide.Left]: leftState,
            [PanelSide.Right]: rightState,
        }),
        [leftState, rightState],
    );

    return <SidePanelsContext.Provider value={value}>{children}</SidePanelsContext.Provider>;
}

export function useSidePanel(side?: PanelSide) {
    const panelsContext = useContext(SidePanelsContext);
    const sideContext = useContext(SideContext);

    if (!panelsContext) {
        throw new Error(`${useSidePanel.name} was used outside of ${SidePanelsContext.displayName} provider`);
    }

    if (side) return panelsContext[side];

    if (sideContext) return panelsContext[sideContext];

    throw new Error(`${useSidePanel.name} was used outside of ${SideContext.displayName} provider`);
}
