import { useDispatch, useSelector } from "react-redux";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { useEffect } from "react";
import { fetchActionParameters } from "../../../actions/nk";
export function useActionCapabilities() {
    const dispatch = useDispatch();

    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);

    useEffect(() => {
        dispatch(fetchActionParameters(scenarioName, scenarioGraph));
    }, [dispatch, scenarioName, scenarioGraph]);
}
