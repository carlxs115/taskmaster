-- Esquema base de TaskMaster (v1.1.1), copiado del DDL que genera Hibernate 6.6 con
-- SQLiteDialect en una BD nueva. No se modifica nunca: cada cambio de esquema va en un
-- V<n> nuevo (ROB-3)

create table activity_log (
    id integer,
    action_type varchar(255) not null check (action_type in ('TASK_CREATED','TASK_EDITED','TASK_DELETED','TASK_PERMANENTLY_DELETED','TASK_RESTORED','TASK_STATUS_CHANGED','DEPENDENCY_ADDED','DEPENDENCY_REMOVED','SUBTASK_CREATED','SUBTASK_EDITED','SUBTASK_DELETED','PROJECT_CREATED','PROJECT_EDITED','PROJECT_DELETED','PROJECT_PERMANENTLY_DELETED','PROJECT_RESTORED','PROJECT_STATUS_CHANGED','PROFILE_UPDATED','PASSWORD_CHANGED','LOGIN','LOGOUT')),
    created_at timestamp not null,
    entity_id bigint,
    entity_name varchar(255),
    entity_type varchar(255),
    new_value varchar(255),
    old_value varchar(255),
    user_id bigint not null,
    primary key (id)
);
create table projects (
    id integer,
    category varchar(255) not null check (category in ('PERSONAL','ESTUDIOS','TRABAJO')),
    created_at timestamp,
    deleted boolean not null,
    deleted_at timestamp,
    description varchar(500),
    estimated_duration numeric(6,2),
    name varchar(255) not null,
    priority varchar(255) not null check (priority in ('LOW','MEDIUM','HIGH','URGENT')),
    status varchar(255) not null check (status in ('TODO','IN_PROGRESS','DONE','SUBMITTED','CANCELLED')),
    user_id bigint not null,
    primary key (id)
);
create table task_dependencies (
    task_id bigint not null,
    depends_on_task_id bigint not null,
    primary key (task_id, depends_on_task_id)
);
create table tasks (
    id integer,
    category varchar(255) not null check (category in ('PERSONAL','ESTUDIOS','TRABAJO')),
    created_at timestamp,
    deleted boolean not null,
    deleted_at timestamp,
    description varchar(1000),
    due_date date,
    estimated_duration numeric(6,2),
    priority varchar(255) not null check (priority in ('LOW','MEDIUM','HIGH','URGENT')),
    status varchar(255) not null check (status in ('TODO','IN_PROGRESS','DONE','SUBMITTED','CANCELLED')),
    title varchar(255) not null,
    parent_task_id bigint,
    project_id bigint,
    user_id bigint not null,
    primary key (id)
);
create table user_settings (
    id integer,
    theme varchar(255) not null check (theme in ('AMATISTA','AMATISTA_DARK','AURORA_BOREALIS','OCEANO','PRADERA','AMBAR','SAKURA','PERLA','ARTICO','NOCHE','VIGILANTE','HACKER','DRAGONSLAYER','LUZ')),
    trash_retention_days integer not null,
    user_id bigint not null unique,
    primary key (id)
);
create table users (
    id integer,
    avatar_path varchar(255),
    birth_date date not null,
    created_at timestamp,
    email varchar(255) not null unique,
    password varchar(255) not null,
    username varchar(255) not null unique,
    primary key (id)
);
create table work_logs (
    id integer,
    activity_type varchar(255) not null check (activity_type in ('ANALYSIS','DESIGN','DEVELOPMENT','TEST','INSTALLATION','DOCUMENTATION','MEETING','SUPPORT','MANAGEMENT')),
    date date not null,
    hours numeric(6,2) not null,
    note TEXT,
    task_id bigint not null,
    primary key (id)
);
create index idx_activity_log_user_id on activity_log (user_id);
create index idx_projects_user_id on projects (user_id);
create index idx_tasks_project_id on tasks (project_id);
create index idx_tasks_user_id on tasks (user_id);
create index idx_users_email on users (email);
