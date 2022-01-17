import { OpenInNew } from "@mui/icons-material";
import { GridRenderCellParams } from "@mui/x-data-grid";
import React from "react";
import { CellLink } from "./cellLink";
import Highlighter from "react-highlight-words";
import { useFilterContext } from "../filters/filtersContext";
import { Highlight } from "./nameCell";
import { scenarioHref } from "./scenarioHref";

export function ScenarioCell(props: GridRenderCellParams): JSX.Element {
    const { getFilter } = useFilterContext();
    const [filter] = getFilter("TEXT", true);
    return (
        <CellLink underline="hover" disabled={!props.value} cellProps={props} href={scenarioHref(props.row.id)}>
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
