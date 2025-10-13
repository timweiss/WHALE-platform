alter table sensor_readings add column local_id uuid;
update sensor_readings set local_id = gen_random_uuid() where local_id is null;
alter table sensor_readings alter column local_id set not null;
create index idx_sensor_readings_local_id on sensor_readings(local_id);
alter table sensor_readings add constraint unique_per_enrolment unique (enrolment_id, local_id);