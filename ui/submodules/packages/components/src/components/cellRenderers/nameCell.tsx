import Highlighter from "react-highlight-words";
import React, { useMemo } from "react";
import { CellLink } from "./cellLink";
import { OpenInNew } from "@mui/icons-material";
import { ExternalLink, scenarioHref } from "../../common";
import { ComponentsFiltersModel } from "../filters";
import { Highlight, IconImg } from "../utils";
import { CellRendererParams } from "../tableWrapper";

export function NameCell(props: CellRendererParams<ComponentsFiltersModel>): JSX.Element {
    const {
        value,
        row,
        filtersContext: { getFilter },
    } = props;

    const filter = useMemo(() => getFilter("NAME", true), [getFilter]);
    const children = useMemo(
        () => (
            <span title={row.componentType}>
                <IconImg src={row.icon} /> <Highlighter textToHighlight={value.toString()} searchWords={filter} highlightTag={Highlight} />
            </span>
        ),
        [filter, row.componentType, row.icon, value],
    );
    const isFragment = row.componentGroupName === "fragments";
    return (
        <CellLink
            component={ExternalLink}
            underline="hover"
            disabled={!isFragment}
            color="inherit"
            cellProps={props}
            href={scenarioHref(value)}
        >
            {isFragment ? (
                <>
                    {children}
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
                </>
            ) : (
                children
            )}
        </CellLink>
    );
}
