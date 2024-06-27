create table esm_questionnaires
(
    id       serial primary key,
    name     text    not null,
    study_id integer references studies (id),
    enabled  boolean not null default true,
    version  integer not null default 1
);

create table esm_elements
(
    id               serial primary key,
    questionnaire_id integer references esm_questionnaires (id),
    type             text    not null,
    step             integer not null,
    position         integer not null,
    configuration    jsonb   not null
);

create table esm_triggers
(
    id               serial primary key,
    questionnaire_id integer references esm_questionnaires (id),
    type             text    not null,
    configuration    jsonb   not null,
    enabled          boolean not null default true
);