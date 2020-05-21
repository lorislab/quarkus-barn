create table todelete
(
    id             SERIAL,
    created        bigint,
    ref            varchar(255),
    primary key (id)
);