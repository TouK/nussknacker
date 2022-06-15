---
sidebar_title: "Machine Learning"
---

## Overview
                              
Nussknacker can evaluate ML models using the Machine Learning component. Internally, the ML Enricher uses  [JPMML-Evaluator library](https://github.com/jpmml/jpmml-evaluator) to evaluate ML models. The ML Enricher is an Enterprise component of Nussknacker and requires a separate licence, contact info@nussknacker.io for more information. 

Models can be either JPMML encoded or exported with H2O Mojo/Pojo. 
Model repository can be one of the following:
- file system
- MLFlow registry
- custom model registry
                                 

## Configuration

Sample configuration:
```
components.prinzPMML {  
    pmmlConfig {
      fileExtension: ".pmml"
      modelsDirectory: "file:///opt/nussknacker/pmml-models"
      modelVersionSeparator: "-v"
    }
  }
```
After ML Enricher is configured, Designer will automatically generate a component for each ML model found and place them in the Enrichers section of the tools palette.
