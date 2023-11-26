import { css } from "@emotion/css";
import React from "react";

export const FixedPortal = () => (
    <div
        id="portal"
        className={css({
            position: "fixed",
            left: 0,
            top: 0,
            zIndex: 9999,
        })}
    />
);
