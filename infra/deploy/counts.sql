-- 모든 public 테이블의 정확한 행 수 (이전/복원 검증에서 두 DB를 비교하는 데 쓴다)
SELECT table_name || ' ' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text
FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1;
