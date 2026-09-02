-- Flowable 8.0.0 官方初始化数据。
-- 执行前提：先执行同级目录 ../schema/schema.sql。

insert into ACT_GE_PROPERTY
values ('common.schema.version', '8.0.0.0', 1);

insert into ACT_GE_PROPERTY
values ('next.dbid', '1', 1);

insert into ACT_GE_PROPERTY
values ('schema.version', '8.0.0.0', 1);

insert into ACT_GE_PROPERTY
values ('schema.history', 'create(8.0.0.0)', 1);

insert into ACT_GE_PROPERTY
values ('dmn.schema.version', '8.0.0.0', 1);
