import {CustomActionDisplayPolicy} from "../types";
import {useSelector} from "react-redux";
import {getProcessName, getProcessVersionId, getScenario} from "../reducers/selectors/graph";
import {ActionType} from "../components/Process/types";

export function resolveCustomActionDisplayability(displayPolicy: CustomActionDisplayPolicy){
  const processVersionId = useSelector(getProcessVersionId);
  const scenario = useSelector(getScenario);

  switch(displayPolicy.type) {
    case "UICustomActionDisplaySimplePolicy":
      const { version, operator, expr } = displayPolicy;
      return false;
    case "UICustomActionDisplayConditionalPolicy":
      return false;
    default:
      return false;
  }
}
