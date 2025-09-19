import { OpenInNew } from "@mui/icons-material";
import Stack from "@mui/material/Stack";
import type { GridRenderCellParams } from "@mui/x-data-grid";
import React from "react";

import { Highlight } from "../../common/highlight";
import { ExternalLink } from "../../common/parentNavigationProvider";
import { scenarioHref } from "../../common/scenarioHref";
import { ScenarioAvatar } from "../../scenarios/list/scenarioAvatar";
import { CellLink } from "../cellRenderers/cellLink";

export function ScenarioCell({ filterText, ...props }: GridRenderCellParams & { filterText: string }): JSX.Element {
    const { row, value, id, ...passProps } = props;
    return (
        <CellLink component={ExternalLink} underline="hover" disabled={!value} href={scenarioHref(row.name)}>
            <Stack direction="row" alignItems="center" {...passProps}>
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
