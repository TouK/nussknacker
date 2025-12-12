import { Box, ToggleButton, ToggleButtonGroup } from "@mui/material";
import { mapValues } from "lodash";
import React, { useMemo, useState } from "react";

import { ContextTree } from "./ContextTree";
import { RawDisplay } from "./RawDisplay";
import type { Direction, VariableContextType } from "./VariableContextTree";

export function ContextDataDisplay({
    context,
    direction,
    inputVariables,
}: {
    context: VariableContextType;
    direction: Direction;
    inputVariables: string[];
}) {
    const data = useMemo(() => mapValues(context?.variables, (v) => v?.pretty), [context?.variables]);
    const [showRawData, setShowRawData] = useState(0);

    return (
        <Box
            sx={{
                position: "relative",
                "&, label": {
                    userSelect: "none",
                },
            }}
        >
            <ToggleButtonGroup
                color="primary"
                value={showRawData}
                size="small"
                sx={(theme) => ({
                    position: "absolute",
                    top: theme.spacing(0.5),
                    right: theme.spacing(0.5),
                    zIndex: 1,
                    background: theme.palette.custom.codeEditor.readonly.backgroundColor,
                    ".MuiButtonBase-root": {
                        paddingY: 0,
                    },
                })}
            >
                <ToggleButton value={0} onClick={(event) => setShowRawData(0)}>
                    tree
                </ToggleButton>
                <ToggleButton value={1} onClick={(event) => setShowRawData(1)}>
                    raw
                </ToggleButton>
            </ToggleButtonGroup>
            {showRawData ? (
                <RawDisplay data={data} />
            ) : (
                <ContextTree data={data} oldFields={direction === "output" ? inputVariables : []} />
            )}
        </Box>
    );
}
