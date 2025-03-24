import { flatMap, uniqBy } from "lodash";
import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import { ToolbarsSide } from "../../reducers/toolbars";
import { Toolbar } from "../toolbarComponents/toolbar";
import { ToolbarConfig, ToolbarsConfig } from "./types";
import { toolbarSelector } from "./ToolbarSelector";
import { getToolbarsConfig } from "../../reducers/selectors/toolbars";

const parseCollection = (collection: ToolbarsConfig): Toolbar[] =>
    uniqBy<Toolbar>(
        flatMap(collection, (toolbars: ToolbarConfig[], defaultSide: ToolbarsSide) =>
            toolbars.map((config) => ({
                ...config,
                defaultSide,
                component: toolbarSelector(config),
                horizontalComponent: toolbarSelector({ ...config, horizontal: true }),
            })),
        ),
        (config) => config.id,
    );

export function useToolbarConfig(): [Toolbar[], string] {
    const config = useSelector(getToolbarsConfig);
    return useMemo(() => {
        const { id, ...toolbarsCollection } = config;
        return [parseCollection(toolbarsCollection), id];
    }, [config]);
}
