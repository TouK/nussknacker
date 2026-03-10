import { alpha, Box, Button, Chip, Typography, useTheme } from "@mui/material";
import React from "react";

import type { TopicEntry } from "./dataMapperUtils";
import { SamplePanel } from "./SampleJsonPanel";

interface TopicPickerPanelProps {
    loading: boolean;
    entries: TopicEntry[];
    onSelect: (entry: TopicEntry) => void;
    onClose: () => void;
}

export function TopicPickerPanel({ loading, entries, onSelect, onClose }: TopicPickerPanelProps): React.JSX.Element {
    const theme = useTheme();
    return (
        <SamplePanel>
            <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 1 }}>
                <Typography sx={{ fontSize: 12 }}>Select a topic to load its output schema as target fields:</Typography>
                <Button size="small" variant="outlined" onClick={onClose} sx={{ fontSize: 11, textTransform: "none", py: "2px" }}>
                    Cancel
                </Button>
            </Box>
            {loading && <Typography sx={{ fontSize: 12, color: "text.secondary", py: 1 }}>Loading topic schemas…</Typography>}
            {!loading && entries.length === 0 && (
                <Typography sx={{ fontSize: 12, color: "text.secondary", py: 1 }}>No topics with defined schemas found.</Typography>
            )}
            {!loading && entries.length > 0 && (
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.75 }}>
                    {entries.map((e) => {
                        const fieldCount = Object.keys(e.schema).length;
                        return (
                            <Chip
                                key={e.topic}
                                label={`${e.topic} (${fieldCount} fields)`}
                                size="small"
                                clickable
                                onClick={() => onSelect(e)}
                                sx={{
                                    fontSize: 12,
                                    backgroundColor: alpha(theme.palette.primary.main, 0.12),
                                    color: theme.palette.primary.light,
                                    "&:hover": { backgroundColor: alpha(theme.palette.primary.main, 0.25) },
                                }}
                            />
                        );
                    })}
                </Box>
            )}
        </SamplePanel>
    );
}
