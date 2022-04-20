import { OpenInNew } from "@mui/icons-material";
import React from "react";
import { CellLink } from "../cellRenderers/cellLink";
import Highlighter from "react-highlight-words";
import { ExternalLink, scenarioHref } from "../../common";
import { UsagesFiltersModel } from "./usagesFiltersModel";
import { Highlight } from "../utils";
import { CellRendererParams } from "../tableWrapper";

export function ScenarioCell({ filtersContext, ...props }: CellRendererParams<UsagesFiltersModel>): JSX.Element {
    const { row, value } = props;
    const { getFilter } = filtersContext;
    const [filter] = getFilter("TEXT", true);
    return (
        <CellLink component={ExternalLink} underline="hover" disabled={!value} cellProps={props} href={scenarioHref(row.id)}>
            <Highlighter autoEscape textToHighlight={value} searchWords={[filter?.toString()]} highlightTag={Highlight} />
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
