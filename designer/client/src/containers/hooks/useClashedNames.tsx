import { useEffect } from "react";

import { fetchScenarios, getScenariosNames } from "../../reducers/scenarios";
import { useAppDispatch, useAppSelector } from "../../store/configureStore";

export function useClashedNames(shouldDownload = true): string[] {
    const dispatch = useAppDispatch();
    const clashedNames = useAppSelector(getScenariosNames);

    useEffect(() => {
        if (shouldDownload) {
            dispatch(fetchScenarios());
        }
    }, [dispatch, shouldDownload]);

    return clashedNames;
}
