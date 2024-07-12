import React, { ReactElement } from "react";
import { styled } from "@mui/material";

const Tip = styled("div")({
    width: 24,
    height: 24,
    svg: {
        // disable svg <title> behavior
        pointerEvents: "none",
    },
});

export default function NodeTip({ icon, ...props }: { className?: string; icon: ReactElement; title: string }) {
    return <Tip {...props}>{icon}</Tip>;
}
