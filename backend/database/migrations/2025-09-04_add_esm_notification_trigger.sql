create table esm_notification_trigger (
    local_id uuid not null,
    enrolment_id integer references enrolments (id),
    trigger_id integer references esm_triggers (id),
    questionnaire_id integer not null,
    added_at bigint not null,
    name text not null,
    status text not null,
    valid_from bigint not null,
    priority text not null,
    modality text not null,
    source text not null,
    source_notification_trigger_id uuid null,
    planned_at bigint null,
    pushed_at bigint null,
    displayed_at bigint null,
    answered_at bigint null,
    updated_at bigint not null,

    constraint esm_notification_trigger_unique primary key (enrolment_id, local_id)
);

alter table esm_answers add column notification_trigger_id uuid null;
alter table esm_answers add constraint esm_answers_notification_trigger_id_fkey foreign key (enrolment_id, notification_trigger_id) references esm_notification_trigger (enrolment_id, local_id);