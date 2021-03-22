import {useSelector} from "react-redux"
import {setFlags} from "../actions/nk/featureFlags"
import {store} from "../bootstrap"
import {FeatureFlags} from "../reducers/featureFlags"
import {featureFlags} from "../reducers/selectors/featureFlags"

export const useFeatureFlags = (): FeatureFlags => useSelector(featureFlags)

// expose to console
window["__FF"] = (flags: FeatureFlags) => store.dispatch(setFlags(flags))
