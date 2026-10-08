-- Server arrival time and an index for per-participant device-time queries
-- ("enrolment X, sensor type Y, device time between A and B").

alter table sensor_readings add column received_at timestamp with time zone default now();

alter table sensor_readings alter column timestamp drop default;
alter table sensor_readings alter column timestamp type bigint using cast(timestamp as bigint);

create index idx_sensor_readings_enrolment_sensor_window on sensor_readings (enrolment_id, sensor_type, timestamp);