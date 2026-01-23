import { Box } from "@mui/material";
import { KBarAnimator, KBarPortal, KBarPositioner, KBarSearch, useKBar } from "kbar";
import React, { useEffect } from "react";

import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppSelector } from "../../store/storeHelpers";
import { ThemedStylesWrapper } from "../ThemedStylesWrapper";
import { RenderResults } from "./RenderResults";

export function CommandBar() {
    const { query } = useKBar();
    const settings = useAppSelector(getUserSettings);

    useEffect(() => {
        query.disable(!settings["experimental.commandBar"]);
    }, [query, settings]);

    return (
        <KBarPortal>
            <Box
                data-no-focus-lock
                sx={(theme) => ({
                    "--mask": "rgb(0 0 0/0.75)",
                    "--background": theme.palette.background.paper,
                    "--foreground": theme.palette.text.primary,
                    "--a1": theme.palette.action.hover,
                    "--shadow": theme.shadows[15],

                    ".MuiToggleButtonGroup-root button": {
                        zoom: 0.75,
                        "&:focus, &:focus-within": {
                            outline: "none",
                        },
                    },
                })}
            >
                <ThemedStylesWrapper
                    component={KBarPositioner}
                    style={(theme) => ({
                        zIndex: theme.zIndex.drawer + 100,
                        background: "var(--mask)",
                        backdropFilter: "blur(1px)",
                    })}
                >
                    <KBarAnimator
                        style={{
                            maxWidth: "600px",
                            width: "100%",
                            background: "var(--background)",
                            backdropFilter: "blur(15px)",
                            color: "var(--foreground)",
                            borderRadius: "8px",
                            overflow: "hidden",
                            boxShadow: "var(--shadow)",
                        }}
                    >
                        <KBarSearch
                            style={{
                                padding: "16px 16px",
                                fontSize: "16px",
                                width: "100%",
                                boxSizing: "border-box",
                                outline: "none",
                                border: "none",
                                background: "var(--background)",
                                color: "var(--foreground)",
                                borderBottom: "2px solid var(--mask)",
                            }}
                        />
                        <RenderResults />
                    </KBarAnimator>
                </ThemedStylesWrapper>
            </Box>
        </KBarPortal>
    );
}
