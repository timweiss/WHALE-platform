# Add Queue for Sensor Reading Ingestion

## Context
The WHALE Android clients synchronize their data at least once a day when connected to Wi-Fi.
The time of day is determined by when the participants sign up.
While this provides some "natural balancing", the clients could upload ~50MB per synchronization.
In addition, the upload is split up in chunks to account for server limitations like maximum payloads beyond our control.
Thus, a 50MB synchronization session could take around 50 requests with payloads of 1MB each to complete. That could quickly exhaust the connection pool.

## Decision
A queue will ensure that we can offload the database insertion to workers to stay in bounds of the connection pool limit.
We will use BullMQ with Redis as it provides on-disk persistence and is recognized as a stable option for handling batch jobs.

For the purpose of sensor reading ingestion, the backend will be split in two paths:
- API endpoint for sensor reading offloading (Producer)
- Separately run worker process(es) handling the database insertion (Consumer)

## Consequences
When deploying, it needs to be ensured that Redis is running when the backend starts accepting requests.
The behaviors are now split for sensor ingestion, which diverges from the basic CRUD nature of the other endpoints.