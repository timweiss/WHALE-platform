alter table studies add column allocation_strategy varchar(50) not null default 'Sequential';

alter table enrolments add column enrolled_at timestamp with time zone not null default now();

alter table study_experimental_groups add column allocation_order integer not null default 0;