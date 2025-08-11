alter table esm_answers add column pending_questionnaire_id uuid not null default '00000000-0000-0000-0000-000000000000';
alter table esm_answers add column last_opened_page int not null default -1;
alter table esm_answers add column status text;
alter table esm_answers add column created_timestamp text;
alter table esm_answers add column last_updated_timestamp text;
alter table esm_answers add column finished_timestamp text;

create index idx_esm_answers_pending_questionnaire_id on esm_answers (pending_questionnaire_id);