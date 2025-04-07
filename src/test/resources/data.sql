-- mid_forecast 테이블 (중기 육상 예보)
INSERT INTO mid_forecast (reg_id, tm_fc, tm_ef, sky, pre, rn_st, created_at)
VALUES ('11B10101', '202504010600', '202504010900', 'WB01', 'WB09', 0, CURRENT_TIMESTAMP);

-- mid_temp_forecast 테이블 (중기 기온 예보)
INSERT INTO mid_temp_forecast (reg_id, tm_fc, tm_ef, min, max, created_at)
VALUES ('11B10101', '202504010600', '202504010900', 5, 15, CURRENT_TIMESTAMP);

-- mid_combined_forecast 테이블 (중기 육상+기온 예보)
INSERT INTO mid_combined_forecast (tm_fc, tm_ef, do_reg_id, si_reg_id, sky, pre, rn_st, min, max, created_at)
VALUES ('202504010600', '202504010900', '11B00000', '11B10101', 'WB01', 'WB09', 0, 5, 15, CURRENT_TIMESTAMP);
