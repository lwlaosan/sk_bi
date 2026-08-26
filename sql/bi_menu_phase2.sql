-- SK BI 阶段2增量菜单（已执行过 bi_menu.sql 的环境使用）
insert into sys_menu values('2010', '报表管理', '2000', '2', 'report', 'bi/report/index', '', '', 1, 0, 'C', '0', '0', 'bi:report:list',   'chart', 'admin', sysdate(), '', null, 'BI报表管理');
insert into sys_menu values('2011', '报表查询', '2010', '1', '',       '',                '', '', 1, 0, 'F', '0', '0', 'bi:report:list',   '#',     'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '报表新建', '2010', '2', '',       '',                '', '', 1, 0, 'F', '0', '0', 'bi:report:create', '#',     'admin', sysdate(), '', null, '');
insert into sys_menu values('2013', '报表设计', '2010', '3', '',       '',                '', '', 1, 0, 'F', '0', '0', 'bi:report:design', '#',     'admin', sysdate(), '', null, '');
insert into sys_menu values('2014', '报表版本', '2010', '4', '',       '',                '', '', 1, 0, 'F', '0', '0', 'bi:report:version','#',     'admin', sysdate(), '', null, '');
insert into sys_menu values('2015', 'SQL选项维护','2010','5', '',       '',                '', '', 1, 0, 'F', '0', '0', 'bi:control:sql',   '#',     'admin', sysdate(), '', null, '');
insert into sys_menu values('2016', '报表运行', '2010', '6', '',       '',                '', '', 1, 0, 'F', '0', '0', 'bi:report:view',   '#',     'admin', sysdate(), '', null, '');
insert into sys_menu values('2017', '报表导出', '2010', '7', '',       '',                '', '', 1, 0, 'F', '0', '0', 'bi:report:export', '#',     'admin', sysdate(), '', null, '');
