import { flatMap, uniqBy } from "lodash";
import { useMemo } from "react";

import { getToolbarsConfig } from "../../reducers/selectors/toolbars";
import type { ToolbarsSide } from "../../reducers/toolbars";
import { useAppSelector } from "../../store/storeHelpers";
import type { Toolbar } from "../toolbarComponents/toolbar";
import type { BuiltinButtonTypes, CustomButtonTypes } from "./buttons/buttonsMap";
import { toolbarSelector } from "./ToolbarSelector";
import type { ToolbarConfig, ToolbarsConfig } from "./types";

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
    const config = useAppSelector(getToolbarsConfig);
    return useMemo(() => {
        const { id, ...toolbarsCollection } = config;
        return [parseCollection(toolbarsCollection), id];
    }, [config]);
}

export function useToolbarHasButton(buttonType: BuiltinButtonTypes | CustomButtonTypes): boolean {
    const [toolbars] = useToolbarConfig();
    return useMemo(() => {
        return toolbars.some((toolbar) => toolbar.buttons?.some((button) => button.type === buttonType));
    }, [buttonType, toolbars]);
}
