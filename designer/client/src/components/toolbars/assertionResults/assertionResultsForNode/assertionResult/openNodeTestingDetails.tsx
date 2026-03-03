import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import React, { useCallback } from "react";
import { NavLink } from "react-router-dom";

import { getScenario } from "../../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { NodeType } from "../../../../../types/node";
import { useWindows } from "../../../../../windowManager/useWindows";
import { ACTIVE_TAB_QUERY_KEY, NodeDetailsTab } from "../../../../graph/node-modal/node/NodeContent/TabsWrapper";

interface Props {
    node: NodeType;
}

export const OpenNodeTestingDetails = ({ node }: Props) => {
    const { openNodeWindow } = useWindows();
    const scenario = useAppSelector(getScenario);

    const openNodeTestingDetails = useCallback(
        (e) => {
            e.stopPropagation();
            openNodeWindow(node, scenario);
        },
        [node, openNodeWindow, scenario],
    );

    return (
        <NavLink
            style={{ display: "flex" }}
            to={`?${ACTIVE_TAB_QUERY_KEY}=${NodeDetailsTab.testing}&nodeId=${node.name}`}
            onClick={openNodeTestingDetails}
        >
            <OpenInNewIcon fontSize={"small"} sx={{ color: (theme) => theme.palette.common.white }} />
        </NavLink>
    );
};
