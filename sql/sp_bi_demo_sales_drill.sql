-- BI 示例存储过程：指标卡、趋势图与三级表格下钻
-- 数据源：bn_dw
-- 下钻路径：ROOT（日期） -> SALE_MAN（业务员） -> CUSTOMER（客户）
--
-- 参数约定：
--   p_user_id       当前若依用户 ID。示例未做数据权限过滤，生产过程应使用该参数限制数据范围。
--   p_region_key    COMPONENT 或 TABLE，由运行时注入。
--   p_component_key 当前组件键，由运行时注入。
--   p_date_start/end 查询控件映射的日期范围。
--   p_drill_field   下钻边的 routeValue；根层为空字符串。
--   p_drill_value   上一层返回的 F_drill_payload JSON 对象；根层为 NULL。

DELIMITER $$

CREATE PROCEDURE sp_bi_demo_sales_drill(
    IN p_user_id       VARCHAR(64),
    IN p_region_key    VARCHAR(16),
    IN p_component_key VARCHAR(64),
    IN p_date_start    DATE,
    IN p_date_end      DATE,
    IN p_drill_field   VARCHAR(64),
    IN p_drill_value   JSON
)
SQL SECURITY INVOKER
READS SQL DATA
COMMENT 'BI示例：销售指标、趋势及日期-业务员-客户三级下钻'
main: BEGIN
    DECLARE v_sale_date DATE DEFAULT NULL;
    DECLARE v_sale_man VARCHAR(100) DEFAULT NULL;

    IF p_date_start IS NULL OR p_date_end IS NULL OR p_date_start > p_date_end THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '日期范围不合法';
    END IF;

    -- 上方组件：同一过程根据稳定的 component_key 返回不同结果结构。
    IF p_region_key = 'COMPONENT' AND p_component_key = 'sales_amount_kpi' THEN
        SELECT COALESCE(SUM(out_amount), 0) AS out_amount
          FROM bn_dw.ads_out_store_goods_by_day
         WHERE sdate BETWEEN p_date_start AND p_date_end;

    ELSEIF p_region_key = 'COMPONENT' AND p_component_key = 'profit_kpi' THEN
        SELECT COALESCE(SUM(profit), 0) AS profit
          FROM bn_dw.ads_out_store_goods_by_day
         WHERE sdate BETWEEN p_date_start AND p_date_end;

    ELSEIF p_region_key = 'COMPONENT' AND p_component_key = 'sales_trend' THEN
        SELECT DATE_FORMAT(sdate, '%Y-%m-%d') AS sdate,
               COALESCE(SUM(out_amount), 0) AS out_amount,
               COALESCE(SUM(profit), 0) AS profit
          FROM bn_dw.ads_out_store_goods_by_day
         WHERE sdate BETWEEN p_date_start AND p_date_end
         GROUP BY sdate
         ORDER BY sdate;

    -- 表格根层：每行携带进入业务员层所需的完整上下文。
    ELSEIF p_region_key = 'TABLE' AND COALESCE(p_drill_field, '') = '' THEN
        SELECT DATE_FORMAT(sdate, '%Y-%m-%d') AS sdate,
               COALESCE(SUM(out_amount), 0) AS out_amount,
               COALESCE(SUM(out_qty), 0) AS out_qty,
               COALESCE(SUM(profit), 0) AS profit,
               JSON_OBJECT('saleDate', DATE_FORMAT(sdate, '%Y-%m-%d')) AS F_drill_payload
          FROM bn_dw.ads_out_store_goods_by_day
         WHERE sdate BETWEEN p_date_start AND p_date_end
         GROUP BY sdate
         ORDER BY sdate;

    -- 第二层：从 JSON 读取选中日期，按业务员汇总。
    ELSEIF p_region_key = 'TABLE' AND p_drill_field = 'sale_man' THEN
        SET v_sale_date = STR_TO_DATE(
            JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.saleDate')),
            '%Y-%m-%d'
        );
        IF v_sale_date IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '下钻载荷缺少 saleDate';
        END IF;

        SELECT DATE_FORMAT(v_sale_date, '%Y-%m-%d') AS sdate,
               COALESCE(NULLIF(sale_man, ''), '未分配') AS sale_man,
               COALESCE(SUM(out_amount), 0) AS out_amount,
               COALESCE(SUM(out_qty), 0) AS out_qty,
               COALESCE(SUM(profit), 0) AS profit,
               JSON_OBJECT(
                   'saleDate', DATE_FORMAT(v_sale_date, '%Y-%m-%d'),
                   'saleMan', COALESCE(sale_man, '')
               ) AS F_drill_payload
          FROM bn_dw.ads_out_store_goods_by_day
         WHERE sdate = v_sale_date
         GROUP BY sale_man
         ORDER BY out_amount DESC;

    -- 第三层：载荷继续携带祖先日期和业务员，返回客户明细汇总。
    ELSEIF p_region_key = 'TABLE' AND p_drill_field = 'customer' THEN
        SET v_sale_date = STR_TO_DATE(
            JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.saleDate')),
            '%Y-%m-%d'
        );
        SET v_sale_man = JSON_UNQUOTE(JSON_EXTRACT(p_drill_value, '$.saleMan'));
        IF v_sale_date IS NULL OR v_sale_man IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '下钻载荷缺少 saleDate 或 saleMan';
        END IF;

        SELECT DATE_FORMAT(v_sale_date, '%Y-%m-%d') AS sdate,
               COALESCE(NULLIF(v_sale_man, ''), '未分配') AS sale_man,
               customer_code,
               customer_name,
               COALESCE(SUM(out_amount), 0) AS out_amount,
               COALESCE(SUM(out_qty), 0) AS out_qty,
               COALESCE(SUM(profit), 0) AS profit
          FROM bn_dw.ads_out_store_goods_by_day
         WHERE sdate = v_sale_date
           AND sale_man <=> NULLIF(v_sale_man, '')
         GROUP BY customer_code, customer_name
         ORDER BY out_amount DESC;

    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '不支持的区域、组件或下钻路由';
    END IF;
END$$

DELIMITER ;
