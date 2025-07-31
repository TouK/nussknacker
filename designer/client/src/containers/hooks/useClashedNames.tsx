import { useEffect } from "react";
import { useSelector } from "react-redux";

import { fetchScenarios, getScenariosNames } from "../../reducers/scenarios";
import { useAppDispatch } from "../../store/configureStore";

export function useClashedNames(shouldDownload = true): string[] {
    const dispatch = useAppDispatch();
    const clashedNames = useSelector(getScenariosNames);

    useEffect(() => {
        if (shouldDownload) {
            dispatch(fetchScenarios());
        }
    }, [dispatch, shouldDownload]);

    return clashedNames;
}
