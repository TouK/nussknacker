import { replaceSearchQuery } from "../../../containers/hooks/useSearchQuery";
import { getScenario } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { useWindows } from "../../../windowManager/useWindows";
import { ACTIVE_TAB_QUERY_KEY, NodeDetailsTab } from "../../graph/node-modal/node/NodeContent/TabsWrapper";

export const useOpenNodeTestingTab = () => {
    const { openNodeWindow } = useWindows();
    const scenario = useAppSelector(getScenario);

    return (node: NodeType) => {
        replaceSearchQuery((current) => ({ ...current, [ACTIVE_TAB_QUERY_KEY]: [NodeDetailsTab.testing] }));
        openNodeWindow(node, scenario);
    };
};
