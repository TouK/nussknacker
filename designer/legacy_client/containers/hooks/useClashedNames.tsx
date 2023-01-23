import {useEffect, useState} from "react"
import HttpService from "../../http/HttpService"

export function useClashedNames(shouldDownload: boolean): string[] {
  const [clashedNames, setClashedNames] = useState<string[]>([])
  useEffect(
    () => {
      if (shouldDownload) {
        HttpService.fetchProcesses().then(processes => {
          const names = processes.data.map(process => process.name)
          setClashedNames(prevState => [].concat(prevState, names))
        })
      }
    },
    [shouldDownload],
  )
  return clashedNames
}
