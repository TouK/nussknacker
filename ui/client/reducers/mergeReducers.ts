import {Reducer, Action} from "../actions/reduxTypes"
import reduceReducers from "reduce-reducers"
import {ReducersMapObject} from "redux"
import {isFunction} from "lodash"

type ReducersObj<S extends {}> = ReducersMapObject<Partial<S>, Action>

function combineReducers<S extends {}>(reducers: ReducersObj<S>, initialState = {} as S): Reducer<S> {
  return (state = initialState, action) => Object
    .keys(reducers)
    .reduce((nextState, key) => {
      const newValue = reducers[key](nextState[key], action)
      return {...nextState, [key]: newValue}
    }, state)
}

export function mergeReducers<S>(...reducers: Array<ReducersObj<S> | Reducer<S>>) {
  return reduceReducers<S>(...reducers.map(
    reducer => isFunction(reducer) ? reducer : combineReducers<S>(reducer),
  ))
}
