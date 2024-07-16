import { css } from "@emotion/css";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { alpha, Box } from "@mui/material";
import NuLogoIcon from "../assets/img/nussknacker-logo-icon.svg";
import { useBuildInfo } from "../containers/BuildInfoProvider";

function useTimer(): [(t: number) => Promise<number>, () => void] {
    const timeout = useRef(null);

    const stop = useCallback(() => {
        clearTimeout(timeout.current);
    }, []);

    const start = useCallback((t: number) => {
        return new Promise<number>((resolve) => {
            timeout.current = setTimeout(() => resolve(t), t);
        });
    }, []);

    useEffect(() => {
        return () => {
            stop();
        };
    }, [stop]);

    return [start, stop];
}

export function VersionInfo({ t = 3000 }: { t?: number }): JSX.Element {
    const buildInfo = useBuildInfo();
    const variedVersions = __BUILD_VERSION__ !== buildInfo?.version;

    const [expanded, setExpanded] = useState(false);
    const [startTimer, stopTimer] = useTimer();

    const hide = useCallback(() => {
        stopTimer();
        startTimer(t).then(() => setExpanded(false));
    }, [startTimer, stopTimer, t]);

    const show = useCallback(() => {
        stopTimer();
        startTimer(t / 4).then(() => {
            setExpanded(true);
            hide();
        });
    }, [hide, startTimer, stopTimer, t]);

    return (
        <Box
            data-testid="version-info"
            sx={(theme) => ({
                "&, div, svg": {
                    transition: "all .25s",
                },

                color: alpha(theme.palette.getContrastText(theme.palette.background.paper), 0.75),
                background: alpha(theme.palette.common.white, expanded ? 0.25 : 0),
                backdropFilter: expanded ? "blur(5px)" : "none",

                position: "absolute",
                bottom: 0,
                right: 0,
                left: 0,
                zIndex: 10,
                overflow: "hidden",

                display: "flex",
                flexDirection: "row",
                alignItems: "center",
                justifyContent: "flex-start",

                whiteSpace: "nowrap",
                fontSize: "75%",

                pointerEvents: expanded ? "auto" : "none",
            })}
            onMouseOver={show}
            onMouseOut={hide}
        >
            <Box
                sx={(theme) => ({
                    pointerEvents: "auto",
                    padding: ".5em .5em .2em .5em",
                    transform: `translateX(${expanded ? 0 : 25}%) translateY(${expanded ? 0 : 45}%) rotate(${expanded ? 0 : -15}deg)`,
                    color: expanded ? "inherit" : alpha(theme.palette.getContrastText(theme.palette.background.paper), 0.25),
                })}
            >
                <NuLogoIcon style={{ height: "1.5rem" }} />
            </Box>
            <div
                className={css({
                    transform: `translateY(${expanded ? 0 : 110}%)`,
                    flex: 1,
                    lineHeight: !variedVersions ? "2.4em" : "1.2em",
                })}
            >
                <div className={css({ fontWeight: "bolder" })}>{variedVersions ? `UI ${__BUILD_VERSION__}` : __BUILD_VERSION__}</div>
                {variedVersions && <div>API {buildInfo?.version}</div>}
            </div>
        </Box>
    );
}
