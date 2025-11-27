import { isEqual } from "lodash";
import { set } from "lodash/fp";
import { useCallback, useEffect, useMemo, useRef } from "react";

import { editProperties } from "../../actions/nk/editProperties";
import { getProperties } from "../../reducers/selectors/graph";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { PropertiesType } from "../../types/node";
import { useCallbackRef } from "../graph/node-modal/node/useCallbackRef";
import { useEditState } from "../graph/node-modal/node/useEditState";
import { NODE_UPDATE_DEBOUNCE_TIMEOUT } from "../graph/node-modal/node/useNodeState";
import { useStream } from "../graph/node-modal/node/useStream";

export const usePropertiesState = () => {
    const dispatch = useAppDispatch();
    const storedProperties = useAppSelector(getProperties);
    const [editedProperties$, setEditedProperties, editedProperties] = useStream<PropertiesType>(storedProperties, true);

    const [isTouchedRef] = useCallbackRef(
        (editedProperties) => {
            return !isEqual(storedProperties, editedProperties);
        },
        [storedProperties],
    );

    const isTouched = useMemo(() => !isEqual(storedProperties, editedProperties), [storedProperties, editedProperties]);

    const [editState, setStatus, editStateRef] = useEditState();

    const handleSetEditedProperties = useCallback(
        (label: string | number, value: string) => {
            setEditedProperties((prevState) => set<typeof editedProperties>(label, value, prevState) as unknown as typeof editedProperties);
        },
        [setEditedProperties],
    );

    const abortControllerRef = useRef<AbortController>(null);

    const settings = useAppSelector(getUserSettings);
    const autoApply = settings["node.autoApply"];

    useEffect(() => {
        if (!autoApply) return;
        const subscription = editedProperties$.observe((editedProperties) => {
            abortControllerRef.current?.abort();
            if (isTouchedRef.current(editedProperties)) {
                setStatus("pending");
            } else {
                setStatus("idle");
            }
        });
        return subscription.unsubscribe;
    }, [autoApply, editedProperties$, isTouchedRef, setStatus]);

    useEffect(() => {
        if (!autoApply) return;
        const subscription = editedProperties$
            .debounce(NODE_UPDATE_DEBOUNCE_TIMEOUT)
            .skipDuplicates(isEqual)
            .observe(async (editedProperties) => {
                const controller = new AbortController();
                abortControllerRef.current = controller;
                setStatus("processing");
                try {
                    await dispatch(editProperties(editedProperties, controller));
                    setStatus("idle");
                } catch (e) {
                    console.error(e);
                    setStatus("error");
                }
            });
        return subscription.unsubscribe;
    }, [autoApply, dispatch, editedProperties$, setStatus]);

    const manualApply = useCallback(async () => await dispatch(editProperties(editedProperties)), [dispatch, editedProperties]);

    return {
        currentProperties: storedProperties,
        editedProperties,
        handleSetEditedProperties,
        isTouched,
        manualApply,
        editState,
        editStateRef,
    };
};
