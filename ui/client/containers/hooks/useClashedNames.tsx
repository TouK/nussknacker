import {useEffect, useState} from "react"
import HttpService from "../../http/HttpService"

export function useClashedNames(shouldDownload: boolean): string[] {
  const [clashedNames, setClashedNames] = useState<string[]>([])
  useEffect(
    () => {
      if (shouldDownload) {
        HttpService.fetchProcessesNames().then(names => {
          setClashedNames(prevState => [].concat(prevState, names))
        })
      }
    },
    [shouldDownload],
  )
  return clashedNames
}
