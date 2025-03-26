import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";

import { fetchScenarios, getScenariosNames } from "../../reducers/scenarios";

export function useClashedNames(shouldDownload = true): string[] {
    const dispatch = useDispatch();
    const clashedNames = useSelector(getScenariosNames);

    useEffect(() => {
        if (shouldDownload) {
            dispatch(fetchScenarios());
        }
    }, [dispatch, shouldDownload]);

    return clashedNames;
}
