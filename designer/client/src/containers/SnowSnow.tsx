import React from "react";
import Snowfall from "react-snowfall";

import { useUserSettings } from "../common/userSettings";

export const SNOW_SNOW_FLAG = "Let it snow!❄️🎄🎅🏼☃️";

export function SnowSnow() {
    const [settings] = useUserSettings();
    const isSnowing = settings["scenario.isItSnowing"];

    if (!isSnowing) return null;

    return (
        <Snowfall
            speed={[0.5, 1.5]}
            opacity={[0.5, 0.75]}
            style={{
                position: "fixed",
                inset: 0,
                zIndex: 9999, // higher than JointJS
                pointerEvents: "none",
            }}
        />
    );
}
