import { OpenInNew } from "@mui/icons-material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import React from "react";
import { CellLink } from "../cellRenderers/cellLink";
import Highlighter from "react-highlight-words";
import { ExternalLink, scenarioHref, useFilterContext } from "../../common";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import { Highlight } from "../utils";

export function ScenarioCell(props: GridRenderCellParams): JSX.Element {
    const { getFilter } = useFilterContext<UsagesFiltersModel>();
    const [filter] = getFilter("TEXT", true);
    return (
        <CellLink component={ExternalLink} underline="hover" disabled={!props.value} cellProps={props} href={scenarioHref(props.row.id)}>
            <Highlighter autoEscape textToHighlight={props.value} searchWords={[filter?.toString()]} highlightTag={Highlight} />
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
        </CellLink>
    );
}
