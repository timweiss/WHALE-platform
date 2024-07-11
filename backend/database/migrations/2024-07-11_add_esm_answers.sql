create table esm_answers
(
    id               serial primary key,
    enrolment_id     integer references enrolments (id),
    questionnaire_id integer references esm_questionnaires (id),
    answers          jsonb     not null,
    created_at       timestamp not null default now()
);