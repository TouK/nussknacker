import { Stack, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { CountsForNodes } from "./CountsForNodes";
import type { ResultsWithId } from "./InputOutputContext";
import type { ValuesContextTreeProps } from "./VariableContextTree";

export function VariableContextTreeHeader({
    direction,
    transitionNodesIds,
}: {
    direction: ValuesContextTreeProps["direction"];
    transitionNodesIds: ResultsWithId[];
}) {
    const { t } = useTranslation();
    return (
        <Stack
            spacing={1}
            sx={{
                position: "sticky",
                top: 0,
                zIndex: 0,
                padding: 1,
            }}
        >
            <Typography variant="subtitle1">
                {direction === "input"
                    ? t("variableContext.header.input", "Incoming records")
                    : t("variableContext.header.output", "Outgoing records")}
            </Typography>
            <CountsForNodes
                nodes={transitionNodesIds.map(({ id, totalCount, results }) => ({
                    id,
                    count: totalCount ?? results?.length,
                }))}
                reversed={direction === "input"}
            />
        </Stack>
    );
}
