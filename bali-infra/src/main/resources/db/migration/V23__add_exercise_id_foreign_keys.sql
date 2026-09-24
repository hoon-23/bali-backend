-- session_logs/template_items.exercise_id에 FK 제약이 없어서, exercises를 직접 삭제하면
-- 참조 무결성 없이 고아 행이 남을 수 있었다(2026-09-24, dev DB에서 PERSONAL 종목 삭제 시 실제 발생).
-- RESTRICT로 걸어서 참조 중인 exercise는 삭제 전에 먼저 참조를 재배정하도록 강제한다.
ALTER TABLE session_logs
    ADD CONSTRAINT fk_session_logs_exercise_id FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE RESTRICT;

ALTER TABLE template_items
    ADD CONSTRAINT fk_template_items_exercise_id FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE RESTRICT;
