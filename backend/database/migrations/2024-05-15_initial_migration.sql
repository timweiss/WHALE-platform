create table studies
(
    id            serial primary key,
    name          text                not null,
    enrolment_key varchar(255) unique not null
);

create table enrolments
(
    id             serial primary key,
    study_id       integer references studies (id),
    participant_id varchar(255) unique not null
);

create table sensor_readings
(
    id           serial primary key,
    enrolment_id integer references enrolments (id),
    sensor_type  text not null,
    data         text not null
);

create table upload_files
(
    id         serial primary key,
    reading_id integer references sensor_readings (id),
    filename   text not null,
    path       text not null
);