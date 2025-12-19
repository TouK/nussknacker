import React from "react";
import Snowfall from "react-snowfall";

import { getUserSettings } from "../reducers/selectors/userSettings";
import { useAppSelector } from "../store/storeHelpers";

export function SnowSnow() {
    const settings = useAppSelector(getUserSettings);
    const isSnowing = settings["scenario.isItSnowing"];

    if (!isSnowing) return null;

    return (
        <Snowfall
            style={{
                position: "fixed",
                inset: 0,
                zIndex: 9999, // higher than JointJS
                pointerEvents: "none",
            }}
        />
    );
}
