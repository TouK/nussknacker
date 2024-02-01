import {CustomActionDisplayPolicy} from "../types";
import {useSelector} from "react-redux";
import {getProcessName, getProcessVersionId, getScenario} from "../reducers/selectors/graph";
import {ActionType} from "../components/Process/types";

export function resolveCustomActionDisplayability(displayPolicy: CustomActionDisplayPolicy){
  const processVersionId = useSelector(getProcessVersionId);
  const scenario = useSelector(getScenario);

  switch(displayPolicy) {
    case CustomActionDisplayPolicy.CurrentlyViewedProcessVersionIsDeployed:
      if (scenario.lastDeployedAction !== null) {
        return scenario.lastDeployedAction.processVersionId === processVersionId &&
          scenario.lastDeployedAction.actionType == ActionType.Deploy
      } else {
        return false;
      }
    default:
      return false;
  }
}
