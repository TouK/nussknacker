import { useDispatch, useSelector } from "react-redux";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { useEffect } from "react";
import { fetchActivityParameters } from "../../../actions/nk";
export function useActivityCapabilities() {
    const dispatch = useDispatch();

    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);

    useEffect(() => {
        dispatch(fetchActivityParameters(scenarioName, scenarioGraph));
    }, [dispatch, scenarioName, scenarioGraph]);
}
