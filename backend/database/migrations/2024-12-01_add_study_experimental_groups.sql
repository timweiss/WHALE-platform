create table study_experimental_groups
(
    id                          serial primary key,
    internal_name               text  not null,
    study_id                    integer references studies (id),
    allocation                  jsonb not null,
    interaction_widget_strategy text  not null
);

alter table enrolments
    add column study_experimental_group_id integer references study_experimental_groups (id);