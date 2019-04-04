-- begin TASKMAN_TASK
alter table TASKMAN_TASK add constraint FK_TASKMAN_TASK_ON_ASSIGNEE foreign key (ASSIGNEE_ID) references SEC_USER(ID)^
create unique index IDX_TASKMAN_TASK_UNIQ_NUMBER_ on TASKMAN_TASK (NUMBER_) ^
create index IDX_TASKMAN_TASK_ON_ASSIGNEE on TASKMAN_TASK (ASSIGNEE_ID)^
-- end TASKMAN_TASK
-- begin TASKMAN_TASK_MESSAGE
alter table TASKMAN_TASK_MESSAGE add constraint FK_TASKMAN_TASK_MESSAGE_ON_TASK foreign key (TASK_ID) references TASKMAN_TASK(ID)^
create index IDX_TASKMAN_TASK_MESSAGE_ON_TASK on TASKMAN_TASK_MESSAGE (TASK_ID)^
-- end TASKMAN_TASK_MESSAGE