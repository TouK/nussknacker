import {Reducer} from "../actions/reduxTypes"
import reduceReducers from "reduce-reducers"
import {isFunction} from "lodash"

export type ReducersMapObject<S> = {
  [K in keyof S]: Reducer<S[K]> | ReducersObj<S[K]>
}

type ReducersObj<S> = ReducersMapObject<Partial<S>>
type ReducerOrObj<S> = ReducersObj<S> | Reducer<S>

function isReducer<S>(reducerLike): reducerLike is Reducer<S> {
  return isFunction(reducerLike)
}

const getReducer = <S>(parse: (reducerLike) => Reducer<S>) => {
  return reducerLike => isReducer<S>(reducerLike) ? reducerLike : parse(reducerLike)
}

function combineReducers<S>(reducers: ReducersObj<S>, initialState = {} as S): Reducer<S> {
  const getter = getReducer(combineReducers)
  return (state = initialState, action) => Object
    .keys(reducers)
    .reduce((nextState, key) => {
      const reducer = getter(reducers[key])
      const newValue = reducer(nextState?.[key], action)
      return {...nextState, [key]: newValue}
    }, state)
}

export function mergeReducers<S>(...reducers: Array<ReducerOrObj<S>>): Reducer<S> {
  return reduceReducers<S>(...reducers.map(getReducer<S>(combineReducers)))
}
