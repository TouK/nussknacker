import { Box } from "@mui/material";
import type { ActionImpl } from "kbar";
import React from "react";

export const ItemShortcuts = ({ action }: { action: ActionImpl }) =>
    !action.shortcut?.length ? null : (
        <Box
            aria-hidden
            sx={{
                display: "grid",
                gridAutoFlow: "column",
                gap: "4px",
                kbd: {
                    padding: "4px 6px",
                    background: "rgba(0 0 0 / .1)",
                    borderRadius: "4px",
                    fontSize: 14,
                },
            }}
        >
            {action.shortcut.map((sc) => (
                <kbd key={sc}>{sc}</kbd>
            ))}
        </Box>
    );
