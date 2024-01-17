import { OpenInNew } from "@mui/icons-material";
import React from "react";
import { CellLink } from "../cellRenderers";
import { ExternalLink, Highlight, scenarioHref } from "../../common";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { ScenarioAvatar } from "../../scenarios/list/scenarioAvatar";
import Stack from "@mui/material/Stack";

export function ScenarioCell({ filterText, ...props }: GridRenderCellParams & { filterText: string }): JSX.Element {
    const { row, value } = props;
    return (
        <CellLink component={ExternalLink} underline="hover" disabled={!value} href={scenarioHref(row.name)}>
            <Stack direction="row" alignItems="center">
                <ScenarioAvatar scenario={row} />
                <Highlight value={value} filterText={filterText} />
                <OpenInNew
                    sx={{
                        height: ".75em",
                        margin: ".25em",
                        verticalAlign: "middle",
                        opacity: 0.1,
                        "a:hover &": {
                            opacity: 0.5,
                        },
                        "a:focus &": {
                            opacity: 0.5,
                        },
                    }}
                />
            </Stack>
        </CellLink>
    );
}
