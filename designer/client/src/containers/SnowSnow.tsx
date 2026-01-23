import React from "react";
import Snowfall from "react-snowfall";

import { ThemedStylesWrapper } from "../components/ThemedStylesWrapper";
import { getUserSettings } from "../reducers/selectors/userSettings";
import { useAppSelector } from "../store/storeHelpers";

export const SNOW_SNOW_FLAG = "Let it snow!❄️🎄🎅🏼☃️";

export function SnowSnow() {
    const settings = useAppSelector(getUserSettings);
    const isSnowing = settings[SNOW_SNOW_FLAG];

    if (!isSnowing) return null;
    return (
        <ThemedStylesWrapper
            component={Snowfall}
            style={(theme) => ({
                zIndex: theme.zIndex.drawer + 101,
                position: "fixed",
                inset: 0,
                pointerEvents: "none",
            })}
        />
    );
}
