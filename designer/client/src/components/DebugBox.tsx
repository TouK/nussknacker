import { Box } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { memo } from "react";

const randomInt = (min: number, max: number) => Math.floor(Math.random() * (max - min)) + min;
const setDepth = (instance: HTMLDivElement) => {
    if (!instance) return;
    const parentDepth = getComputedStyle(instance.parentElement).getPropertyValue("--depth");
    const numericDepth = parseInt(parentDepth || "0", 10);
    instance.style.setProperty("--depth", `${numericDepth + 1}`);
};

export const DebugBox = memo(function DebugBox({ children }: PropsWithChildren) {
    return (
        <Box
            ref={setDepth}
            sx={{
                outlineWidth: 2,
                outlineStyle: "solid",
                outlineOffset: "calc(var(--depth, 0) * -2px)",
            }}
            style={{
                outlineColor: `rgba(${randomInt(0, 255)}, ${randomInt(0, 255)}, ${randomInt(0, 255)})`,
            }}
        >
            {children}
        </Box>
    );
});
