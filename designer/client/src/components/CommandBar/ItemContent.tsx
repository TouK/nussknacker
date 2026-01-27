import { Box, Stack } from "@mui/material";
import type { ActionImpl } from "kbar";
import React from "react";

export const ItemContent = ({ action, ancestors }: { action: ActionImpl; ancestors: ActionImpl[] }) => (
    <Box
        sx={{
            display: "flex",
            gap: "8px",
            alignItems: "center",
            fontSize: 14,
        }}
    >
        {action.icon ? (
            <Box
                sx={{
                    width: "1.5em",
                    height: "1.5em",
                    opacity: 0.5,
                    display: "flex",
                    alignItems: "center",
                    "& > *": { height: "100%", width: "100%" },
                }}
            >
                {action.icon}
            </Box>
        ) : null}
        <Stack>
            <Box>
                {ancestors.map((ancestor) => (
                    <React.Fragment key={ancestor.id}>
                        <Box
                            component="span"
                            sx={{
                                opacity: 0.5,
                                marginRight: 1,
                            }}
                        >
                            {ancestor.name}
                        </Box>
                        <Box
                            component="span"
                            sx={{
                                marginRight: 1,
                            }}
                        >
                            &rsaquo;
                        </Box>
                    </React.Fragment>
                ))}
                <Box component="span">{action.children.length ? `${action.name}…` : action.name}</Box>
            </Box>
            {action.subtitle ? (
                <Box component="span" sx={{ fontSize: 12 }}>
                    {action.subtitle}
                </Box>
            ) : null}
        </Stack>
    </Box>
);
