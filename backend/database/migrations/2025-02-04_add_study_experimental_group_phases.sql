-- remove deprecated columns
alter table study_experimental_groups drop column interaction_widget_strategy;
alter table study_experimental_groups drop column allocation;

create table study_experimental_group_phases
(
    id                          serial primary key,
    experimental_group_id       integer      not null references study_experimental_groups (id),
    internal_name               varchar(255) not null,
    from_day                    integer      not null,
    duration_days               integer      not null,
    interaction_widget_strategy varchar(50)  not null
);