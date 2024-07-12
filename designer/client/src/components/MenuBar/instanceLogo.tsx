import React, { ComponentProps, ReactNode, useState } from "react";
import { absoluteBePath } from "../../common/UrlUtils";
import { styled } from "@mui/material";

const Logo = styled("img")({
    height: "1.5em",
    maxWidth: 150,
});

enum State {
    PENDING,
    ERROR,
    SUCCESS,
}

export function InstanceLogo({
    fallback = null,
    ...props
}: {
    fallback?: ReactNode;
} & ComponentProps<typeof Logo>) {
    const [logoState, setLogoState] = useState<State>(State.PENDING);

    if (logoState === State.ERROR) {
        return <>{fallback}</>;
    }

    return (
        <Logo
            src={absoluteBePath("/assets/img/instance-logo.svg")}
            {...props}
            sx={{ display: logoState === State.SUCCESS ? "inline-block" : "none" }}
            onLoad={() => setLogoState(State.SUCCESS)}
            onError={() => setLogoState(State.ERROR)}
        />
    );
}
