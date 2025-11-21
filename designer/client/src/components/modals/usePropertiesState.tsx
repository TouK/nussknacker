import { isEqual } from "lodash";
import { set } from "lodash/fp";
import { useCallback, useEffect, useMemo } from "react";

import { editProperties } from "../../actions/nk/editProperties";
import { getProperties } from "../../reducers/selectors/graph";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { PropertiesType } from "../../types/node";
import { NODE_UPDATE_DEBOUNCE_TIMEOUT } from "../graph/node-modal/node/useNodeState";
import { useStream } from "../graph/node-modal/node/useStream";

export const usePropertiesState = () => {
    const dispatch = useAppDispatch();
    const currentProperties = useAppSelector(getProperties);
    const [editedProperties$, setEditedProperties, editedProperties] = useStream<PropertiesType>(currentProperties, true);
    const isTouched = useMemo(() => !isEqual(currentProperties, editedProperties), [currentProperties, editedProperties]);

    const handleSetEditedProperties = useCallback(
        (label: string | number, value: string) => {
            setEditedProperties((prevState) => set<typeof editedProperties>(label, value, prevState) as unknown as typeof editedProperties);
        },
        [setEditedProperties],
    );

    const settings = useAppSelector(getUserSettings);
    const autoApply = settings["node.autoApply"];

    useEffect(() => {
        if (!autoApply) return;
        const subscription = editedProperties$
            .debounce(NODE_UPDATE_DEBOUNCE_TIMEOUT)
            .skipDuplicates(isEqual)
            .observe((editedProperties) => {
                dispatch(editProperties(editedProperties));
            });
        return subscription.unsubscribe;
    }, [autoApply, dispatch, editedProperties$]);

    const manualApply = useCallback(async () => await dispatch(editProperties(editedProperties)), [dispatch, editedProperties]);

    return { currentProperties, editedProperties, handleSetEditedProperties, isTouched, manualApply };
};
