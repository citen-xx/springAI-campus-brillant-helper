-- 校园智能知识库助手：student 与 sys_user 绑定补丁
-- 注意：
-- 1. 当前仓库未找到 student 表的建表 SQL，请先在真实数据库确认 student 表已存在，再执行下面的 ALTER TABLE。
-- 2. 如果真实库已经存在 user_id 字段或唯一索引，请按实际情况跳过对应语句。

ALTER TABLE student
    ADD COLUMN user_id BIGINT(20) NULL COMMENT '绑定 sys_user.user_id';

ALTER TABLE student
    ADD UNIQUE KEY uk_student_user_id (user_id);

-- 可选：初始化 student 角色，供学生自助接口与学生聊天接口使用。
INSERT INTO sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
     status, del_flag, create_by, create_time, update_by, update_time, remark)
SELECT
    '学生角色', 'student', 3, '1', 1, 1,
    '0', '0', 'admin', NOW(), '', NULL, '学生自助权限角色'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_key = 'student'
);

-- 可选：将已经绑定 user_id 的学生账号自动加入 student 角色。
INSERT INTO sys_user_role (user_id, role_id)
SELECT s.user_id, r.role_id
FROM student s
JOIN sys_role r ON r.role_key = 'student'
LEFT JOIN sys_user_role sur ON sur.user_id = s.user_id AND sur.role_id = r.role_id
WHERE s.user_id IS NOT NULL
  AND sur.user_id IS NULL;
