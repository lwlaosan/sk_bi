ALTER TABLE bi_route
    DROP CHECK ck_bi_route_view,
    ADD CONSTRAINT ck_bi_route_view CHECK (
        view_type IN ('TABLE','BAR','STACKED_BAR','HORIZONTAL_BAR','LINE','AREA','PIE','DONUT','GAUGE','KPI')
    );
