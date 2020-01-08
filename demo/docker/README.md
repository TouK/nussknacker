This is part of Nussknacker's [quickstart](https://touk.github.io/nussknacker/Quickstart.html).

Images
=====
Docker images are published on each commit. You can find them at https://hub.docker.com/r/touk/nussknacker/tags. Default image tag is: demo-latest.  

Demo version
=======
Demo version available at: https://demo.nussknacker.io. You can sign in by Github

Running
=======
* Env variable NUSSKNACKER_VERSION has to point to docker tag with model. You can set it in .env file
* You can change each components version like kafka / flink / etc.. by setting corresponding env at .env file
* Full env: `docker-compose -f docker-compose.yml -f docker-compose-env.yml up -d` // Full env tests
* App env: `docker-compose up -d` // Only launch standalone application (available on 3081 port)
* Dependencies envs: `docker-compose -f docker-compose-env.yml up -d` // Envs for dev integration tests
* App env with generic model: `NUSSKNACKER_CONFIG_FILE=/opt/nussknacker/conf/application.conf docker-compose up -d` // Launch standalone application with generic model