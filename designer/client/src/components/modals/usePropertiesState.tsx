import { isEqual } from "lodash";
import { set } from "lodash/fp";
import { useCallback, useMemo } from "react";

import { editProperties } from "../../actions/nk/editProperties";
import { getProperties } from "../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import type { PropertiesType } from "../../types/node";
import { useStream } from "../graph/node-modal/node/useStream";

export const usePropertiesState = (): {
    currentProperties;
    editedProperties: PropertiesType;
    handleSetEditedProperties;
    isTouched: boolean;
    manualApply;
    editState: "idle" | "processing" | "pending" | "error";
} => {
    const dispatch = useAppDispatch();
    const storedProperties = useAppSelector(getProperties);
    const [editedProperties$, setEditedProperties, editedProperties] = useStream<PropertiesType>(storedProperties, true);

    const isTouched = useMemo(() => !isEqual(storedProperties, editedProperties), [storedProperties, editedProperties]);

    const handleSetEditedProperties = useCallback(
        (label: string | number, value: string) => {
            setEditedProperties((prevState) => set<typeof editedProperties>(label, value, prevState) as unknown as typeof editedProperties);
        },
        [setEditedProperties],
    );

    const manualApply = useCallback(async () => await dispatch(editProperties(editedProperties)), [dispatch, editedProperties]);

    return {
        currentProperties: storedProperties,
        editedProperties,
        handleSetEditedProperties,
        isTouched,
        manualApply,
        editState: isTouched ? "pending" : "idle",
    };
};
