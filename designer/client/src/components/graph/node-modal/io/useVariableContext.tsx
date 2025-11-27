import { useCallback, useEffect, useMemo, useState } from "react";

import { Initiator } from "../../../../actions/nk/liveData";
import { getPauseReasons } from "../../../../reducers/selectors/getLiveData";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useInputOutputContext } from "./InputOutputContext";
import type { Direction, VariableContextType } from "./VariableContextTree";

export function useVariableContext(direction: Direction) {
    const {
        state: { inputDataSetId, outputDataSetId, inputVariables },
        dispatch,
        getAvailableContexts,
        inputNodesIds,
        outputNodesIds,
    } = useInputOutputContext();

    const value = useMemo(() => (direction === "input" ? inputDataSetId : outputDataSetId), [direction, inputDataSetId, outputDataSetId]);
    const [availableContexts, hiddenAvailableContexts] = useMemo(() => {
        const r = getAvailableContexts(direction);
        const contexts = r[0].sort((b, a) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());

        if (contexts.length < 1) {
            return [contexts, 0];
        }

        const pageSize = 30;
        if (contexts.length > pageSize) {
            const sliced = contexts.filter((r) => !r.disabled).slice(0, pageSize);
            return [sliced, r[1] - sliced.length];
        }

        return [contexts, r[1] - contexts.length];
    }, [direction, getAvailableContexts]);
    const enabledContexts = useMemo(() => availableContexts.filter((c) => !c.disabled), [availableContexts]);

    const [selectedContextCache, setSelectedContextCache] = useState<VariableContextType>(null);

    const liveDataPausedBy = useAppSelector(getPauseReasons);
    useEffect(() => {
        setSelectedContextCache((selected) => {
            if (enabledContexts.find((r) => r.id === selected?.id)) return selected;
            if (liveDataPausedBy.includes(direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion)) {
                return enabledContexts[0];
            }
            return selected;
        });
    }, [direction, enabledContexts, liveDataPausedBy]);

    const setContext = useCallback(
        (context: VariableContextType) => {
            setSelectedContextCache(context);
            dispatch({
                type: direction === "input" ? "selectInputContext" : "selectOutputContext",
                context,
            });
        },
        [direction, dispatch],
    );

    const transitionNodesIds = useMemo(() => {
        const transitions = direction === "input" ? inputNodesIds : outputNodesIds;
        return transitions.filter(({ id, results }) => id || results?.length);
    }, [direction, inputNodesIds, outputNodesIds]);

    return {
        value,
        selectedContextCache,
        availableContexts,
        hiddenAvailableContexts,
        setContext,
        inputVariables,
        transitionNodesIds,
    };
}
