import Highlighter from "react-highlight-words";
import React from "react";
import { GridRenderCellParams } from "@mui/x-data-grid";
import { CellLink } from "./cellLink";
import { OpenInNew } from "@mui/icons-material";
import { ExternalLink, scenarioHref, useFilterContext } from "../../common";
import { ComponentsFiltersModel } from "../filters";
import { Highlight, IconImg } from "../utils";

export function NameCell(props: GridRenderCellParams): JSX.Element {
    const { value, row } = props;
    const { getFilter } = useFilterContext<ComponentsFiltersModel>();
    const children = (
        <span title={row.componentType}>
            <IconImg src={row.icon} />{" "}
            <Highlighter textToHighlight={value.toString()} searchWords={getFilter("NAME", true)} highlightTag={Highlight} />
        </span>
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
