import {CustomActionDisplayPolicy} from "../types";
import {useSelector} from "react-redux";
import {getProcessVersionId} from "../reducers/selectors/graph";

export function resolveCustomActionDisplayability(displayPolicy: CustomActionDisplayPolicy){
  const versionId = useSelector(getProcessVersionId);

  switch(displayPolicy) {
    case CustomActionDisplayPolicy.CurrentlyViewedProcessVersionIsDeployed:
      //TODO
      return true;
    default:
      break;
  }
}
