import React from "react";
import loadable from "@loadable/component";
import { styled } from "@mui/material";
import { PlaceholderIconFallbackComponent } from "./PlaceholderIconFallbackComponent";

// This retry mechanism was introduced as sometimes dynamic import would fail.
const Icon = loadable(() =>
    import("nussknackerUi/Icon").catch(async (error) => {
        console.warn("Failed to import Icon submodule. Import retry will be carried out", error);
        try {
            return await import("nussknackerUi/Icon");
        } catch (e) {
            console.error("Import retry failed", e);
            return { default: () => <PlaceholderIconFallbackComponent /> };
        }
    }),
);
export const NuIcon = styled(Icon)(({ theme }) => ({
    width: "1em",
    height: "1em",
    color: "inherit",
}));
