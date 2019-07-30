import {API_URL} from "./config"
import axios from 'axios'

const configuration = {
  withCredentials: true,
  baseURL: API_URL
}

export default axios.create(configuration)