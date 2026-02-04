import React from "react";
import Snowfall from "react-snowfall";

import { useUserSettings } from "../common/useUserSettings";
import { ThemedStylesWrapper } from "../components/ThemedStylesWrapper";

export const SNOW_SNOW_FLAG = "Let it snow!❄️🎄🎅🏼☃️";

export function SnowSnow() {
    const [isSnowing] = useUserSettings(SNOW_SNOW_FLAG);

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
