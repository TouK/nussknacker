import { OpenInNew } from "@mui/icons-material";
import { Stack } from "@mui/material";
import React, { useMemo } from "react";

import { Highlight } from "../../common/highlight";
import { ExternalLink } from "../../common/parentNavigationProvider";
import { scenarioHref } from "../../common/scenarioHref";
import { ComponentAvatar } from "../../scenarios/list/componentAvatar";
import { useComponentsFilterContext } from "../filters/useComponentsFilterContext";
import type { CellRendererParams } from "../tableWrapper";
import { CellLink } from "./cellLink";

export function NameCell(props: CellRendererParams): JSX.Element {
    const { value, row } = props;
    const { getFilter } = useComponentsFilterContext();

    const filter = useMemo(() => getFilter("NAME"), [getFilter]);
    const isFragment = row.componentType === "fragment";
    const label = row.label ? row.label : value;
    return (
        <CellLink component={ExternalLink} underline="hover" disabled={!isFragment} color="inherit" href={scenarioHref(value)}>
            <Stack direction="row" alignItems="center" fontSize={"1.25rem"}>
                <ComponentAvatar src={row.icon} title={row.componentType} fragment={isFragment} />
                <Highlight value={label} filterText={filter} />
                {isFragment ? (
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
                ) : null}
            </Stack>
        </CellLink>
    );
}
